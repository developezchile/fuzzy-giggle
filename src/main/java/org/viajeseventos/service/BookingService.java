package org.viajeseventos.service;

import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ForbiddenException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingStatus;
import org.viajeseventos.model.Event;
import org.viajeseventos.repository.BookingRepository;
import org.viajeseventos.repository.EventRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Bus trip bookings. The EVENTS module covers a user's own bookings (book, list, cancel); the
 * BOOKINGS module is the operator's view of everyone's. "Today" comes from the injected clock —
 * Chile time in production — so an event stays bookable through its last day.
 */
public final class BookingService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public BookingService(EventRepository eventRepository, BookingRepository bookingRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    public List<EventRepository.Listing> upcomingEvents(long userId) {
        return eventRepository.findUpcoming(userId, today());
    }

    public List<Event> allEvents() {
        return eventRepository.findAll();
    }

    public Booking create(long userId, long eventId, CreateBookingRequest request) {
        Event event = eventRepository.findById(eventId)
                .filter(Event::active)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        if (event.hasEnded(today())) {
            throw new BusinessRuleException("El evento ya finalizó, no se pueden registrar viajes");
        }
        long bookingId = bookingRepository.insert(event.id(), userId, request.passengers);
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    public List<Booking> myBookings(long userId) {
        return bookingRepository.findByUserId(userId);
    }

    /** Only the account that made the booking can cancel it, and only until the event ends. */
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
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    public List<Booking> allBookings(Long eventId) {
        return bookingRepository.findAll(eventId);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
