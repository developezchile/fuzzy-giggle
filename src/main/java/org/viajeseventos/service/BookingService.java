package org.viajeseventos.service;

import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ForbiddenException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingPassenger;
import org.viajeseventos.model.BookingStatus;
import org.viajeseventos.model.Event;
import org.viajeseventos.model.Trip;
import org.viajeseventos.model.TripStatus;
import org.viajeseventos.model.TripStop;
import org.viajeseventos.model.WaitlistEntry;
import org.viajeseventos.notify.TripNotifier;
import org.viajeseventos.repository.BookingRepository;
import org.viajeseventos.repository.TripRepository;
import org.viajeseventos.repository.WaitlistRepository;
import org.viajeseventos.security.Caller;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Booking seats on a trip. The MY_BOOKINGS module covers a client's own side (book, list, cancel,
 * wait for a seat); the BOOKINGS module is the operator's view of everyone's. "Now" comes from the
 * injected clock — Chile time in production — so an event stays bookable through its last day.
 *
 * <p>Since V8 a booking takes seats from a trip's fixed capacity, so two things that used to be
 * impossible to get wrong now matter: the count has to be taken inside the same transaction that
 * writes the passengers (that's {@link BookingRepository#insert}, which locks the trip row), and a
 * client who finds the bus full has to land somewhere — the waitlist — instead of a dead end.
 */
public final class BookingService {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    /** One event as a client sees it: its open trips, and how many passengers they already have on it. */
    public record EventWithTrips(Event event, int myPassengerCount, List<Trip> trips) {
    }

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final WaitlistRepository waitlistRepository;
    private final TripNotifier notifier;
    private final Clock clock;

    public BookingService(TripRepository tripRepository, BookingRepository bookingRepository,
                          WaitlistRepository waitlistRepository, TripNotifier notifier, Clock clock) {
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.waitlistRepository = waitlistRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * The company's upcoming events, each with the trips a client could still take — including the
     * full ones, which is where the waitlist is offered. Events with no open trip are left out:
     * there's nothing to book.
     */
    public List<EventWithTrips> upcomingEvents(Caller caller) {
        List<Trip> trips = tripRepository.findBookable(caller.companyId(), today());
        Map<Long, Integer> mine = tripRepository.myPassengersByEvent(caller.companyId(), caller.userId());

        Map<Long, List<Trip>> byEvent = new LinkedHashMap<>();
        Map<Long, Event> events = new LinkedHashMap<>();
        for (Trip trip : trips) {
            events.putIfAbsent(trip.event().id(), trip.event());
            byEvent.computeIfAbsent(trip.event().id(), k -> new ArrayList<>()).add(trip);
        }
        List<EventWithTrips> listings = new ArrayList<>(events.size());
        events.forEach((eventId, event) ->
                listings.add(new EventWithTrips(event, mine.getOrDefault(eventId, 0), byEvent.get(eventId))));
        return listings;
    }

    /** One trip, for the booking modal — the caller's company only. */
    public Trip trip(Caller caller, long tripId) {
        return tripRepository.findById(caller.companyId(), tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Salida no encontrada"));
    }

    /**
     * Books the passengers on a trip. Everything that can refuse the booking is checked in the
     * order the client would want to hear it: the trip is open, the event hasn't passed, the
     * deadline hasn't passed, the stops are real, and only then — inside the transaction — whether
     * the seats are actually there.
     */
    public Booking create(Caller caller, long tripId, CreateBookingRequest request) {
        Trip trip = trip(caller, tripId);
        LocalDateTime now = now();

        if (!trip.status().takesBookings()) {
            throw new BusinessRuleException(trip.status() == TripStatus.CANCELLED
                    ? "La salida fue cancelada"
                    : "La salida todavía no está publicada");
        }
        if (!trip.event().active()) {
            throw new BusinessRuleException("El evento ya no está disponible");
        }
        if (trip.event().hasEnded(today())) {
            throw new BusinessRuleException("El evento ya finalizó, no se pueden registrar viajes");
        }
        if (trip.pastDeadline(now)) {
            throw new BusinessRuleException("Las reservas para esta salida cerraron el "
                    + DAY_TIME.format(trip.bookingDeadline()));
        }

        List<BookingPassenger> passengers = resolveStops(trip, request);
        BookingRepository.InsertResult result = bookingRepository.insert(trip.id(), caller.userId(), passengers);
        if (!result.created()) {
            throw new BusinessRuleException(fullMessage(result.seatsLeft(), passengers.size()));
        }

        // Booking a seat takes the person off the queue they were in for it.
        waitlistRepository.remove(trip.id(), caller.userId());
        confirmIfQuorumReached(trip);

        Booking booking = bookingRepository.findById(result.bookingId()).orElseThrow();
        notifier.bookingConfirmed(booking);
        return booking;
    }

    public List<Booking> myBookings(long userId) {
        return bookingRepository.findByUserId(userId);
    }

    /**
     * Only the account that made the booking can cancel it, and only until the event ends.
     * Cancelling frees its seats, so whoever is waiting for them is told — the other half of the
     * waitlist, and the reason it's worth having one.
     */
    public Booking cancel(long userId, long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        if (booking.userId() != userId) {
            throw new ForbiddenException("Solo puedes cancelar tus propias reservas");
        }
        if (booking.status() == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("La reserva ya está cancelada");
        }
        if (booking.event().hasEnded(today())) {
            throw new BusinessRuleException("El evento ya finalizó, la reserva no se puede cancelar");
        }
        bookingRepository.cancel(bookingId);
        offerFreedSeats(booking.trip().id());
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    /** Every departure of the company — the operator's booking filter. */
    public List<Trip> allTrips(long companyId) {
        return tripRepository.findAll(companyId, null);
    }

    /** The operator's list: every booking of the company, narrowed to one trip or one event. */
    public List<Booking> allBookings(long companyId, Long tripId, Long eventId) {
        return bookingRepository.findAll(companyId, tripId, eventId);
    }

    /** Takes a place in the queue for a full trip. Asking for a seat that exists is just a booking. */
    public void joinWaitlist(Caller caller, long tripId, int seats) {
        Trip trip = trip(caller, tripId);
        if (!trip.status().takesBookings()) {
            throw new BusinessRuleException("La salida no está recibiendo reservas");
        }
        if (trip.event().hasEnded(today())) {
            throw new BusinessRuleException("El evento ya finalizó");
        }
        if (!trip.full()) {
            throw new BusinessRuleException("La salida todavía tiene cupos: reserva directamente.");
        }
        if (seats < 1 || seats > CreateBookingRequest.MAX_PASSENGERS) {
            throw new ValidationException(Map.of("seats",
                    "debe estar entre 1 y " + CreateBookingRequest.MAX_PASSENGERS));
        }
        waitlistRepository.add(tripId, caller.userId(), seats);
    }

    public void leaveWaitlist(Caller caller, long tripId) {
        trip(caller, tripId);
        waitlistRepository.remove(tripId, caller.userId());
    }

    /** The trips the account is waiting on, so the UI can show "estás en lista de espera". */
    public Set<Long> waitlistedTripIds(long userId) {
        return Set.copyOf(waitlistRepository.tripIdsForUser(userId));
    }

    public List<WaitlistEntry> waitlist(long companyId, long tripId) {
        tripRepository.findById(companyId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Salida no encontrada"));
        return waitlistRepository.findByTrip(tripId);
    }

    /**
     * Turns each passenger's chosen stop into the trip's actual stop, and copies its place and time
     * onto the passenger as the snapshot of what they were told. A stop from another trip (or one
     * the operator deleted between loading the page and submitting) is reported under the same key
     * the form uses for that field.
     */
    private List<BookingPassenger> resolveStops(Trip trip, CreateBookingRequest request) {
        if (trip.stops().isEmpty()) {
            throw new BusinessRuleException("La salida todavía no tiene paradas cargadas");
        }
        var errors = new LinkedHashMap<String, String>();
        List<BookingPassenger> passengers = new ArrayList<>(request.passengers.size());
        for (var input : request.passengers) {
            TripStop stop = input.stopId() == null ? null : trip.stop(input.stopId());
            if (stop == null) {
                errors.put("passengers." + (input.position() - 1) + ".stopId",
                        "Elige una parada de esta salida");
                continue;
            }
            passengers.add(BookingPassenger.booking(input.position(), input.fullName(), input.phone(), stop.id(),
                    stop.place(), stop.pickupAt(), input.returnPlace(), trip.priceAt(stop)));
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return passengers;
    }

    /** Quorum reached: the bus goes, and the trip says so from now on. */
    private void confirmIfQuorumReached(Trip trip) {
        if (trip.status() != TripStatus.PUBLISHED || trip.minSeats() <= 0) return;
        int taken = bookingRepository.countSeats(trip.id());
        if (taken >= trip.minSeats()) {
            tripRepository.updateStatus(trip.companyId(), trip.id(), TripStatus.CONFIRMED);
        }
    }

    /**
     * Walks the queue oldest first and tells everyone whose group still fits in what just opened
     * up. Nobody is moved into the trip automatically: they're told a seat exists and they book it,
     * which keeps one person's stale group size from silently eating the seats.
     */
    private void offerFreedSeats(long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null || !trip.status().takesBookings()) return;
        int seatsLeft = trip.seatsLeft();
        if (seatsLeft <= 0) return;

        for (WaitlistEntry entry : waitlistRepository.findByTrip(tripId)) {
            if (entry.seats() > seatsLeft) continue;
            notifier.seatAvailable(trip, entry);
            waitlistRepository.markNotified(entry.id());
        }
    }

    private String fullMessage(int seatsLeft, int wanted) {
        if (seatsLeft == 0) {
            return "La salida se llenó. Puedes anotarte en la lista de espera y te avisamos si se libera un cupo.";
        }
        return "Quedan " + seatsLeft + (seatsLeft == 1 ? " asiento" : " asientos") + " y estás reservando "
                + wanted + ". Quita pasajeros o anótate en la lista de espera.";
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
