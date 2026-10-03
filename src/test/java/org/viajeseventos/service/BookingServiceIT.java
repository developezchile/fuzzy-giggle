package org.viajeseventos.service;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ForbiddenException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingStatus;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.Trip;
import org.viajeseventos.model.TripStatus;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.EventRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.Caller;
import org.viajeseventos.testsupport.FakeEmailSender;
import org.viajeseventos.testsupport.TestDb;
import org.viajeseventos.testsupport.TripFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bookings against a real local Postgres (see {@link TestDb}), with "now" pinned by a fixed clock.
 * Since V7 a booking takes seats from a departure's capacity, so most of what's worth testing here
 * is what happens at the edge of that capacity.
 */
class BookingServiceIT {

    private static ConnectionPool pool;
    private static EventRepository eventRepository;
    private static UserRepository userRepository;
    private static TripService tripService;
    private static FakeEmailSender emails;
    private static long clientProfileId;
    private static long companyId;
    /** 2026-10-01 — before every seeded event. */
    private static BookingService service;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        eventRepository = new EventRepository(pool);
        userRepository = new UserRepository(pool);
        tripService = TripFixtures.tripService(pool);
        emails = new FakeEmailSender();
        clientProfileId = new ProfileRepository(pool).findByCode(Profile.CLIENT).orElseThrow().getId();
        companyId = TestDb.seededCompanyId(pool);
        service = TripFixtures.bookingService(pool, "2026-10-01T12:00:00Z", emails);
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void bookingStoresEveryPassengerAndCountsTowardsTheEvent() {
        Caller user = newUser();
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40);

        Booking booking = service.create(user, trip.id(), request(trip, "Ana Pérez", "Juan Soto"));

        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(2, booking.passengers().size());
        assertEquals("+56 9 1234 5678", booking.passengers().getFirst().phone());
        assertEquals(trip.id(), booking.trip().id());
        assertEquals(2, myCount(user, trip.event().id()));
        assertEquals(List.of(booking.id()), service.myBookings(user.userId()).stream().map(Booking::id).toList());
        assertTrue(service.allBookings(companyId, trip.id(), null).stream().anyMatch(b -> b.id() == booking.id()));
    }

    /** The stop the passenger picked is copied onto them, so a later edit can't rewrite the ticket. */
    @Test
    void thePassengersStopBecomesTheirDeparturePlaceAndTime() {
        Caller user = newUser();
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40);
        var stop = trip.stops().getFirst();

        var passenger = service.create(user, trip.id(), request(trip, "Ana Pérez")).passengers().getFirst();

        assertEquals(stop.id(), passenger.stopId());
        assertEquals("Terminal Rodoviario", passenger.departurePlace());
        assertEquals(stop.pickupAt(), passenger.departureTime());
    }

    @Test
    void aStopFromAnotherDepartureIsRejected() {
        Caller user = newUser();
        Trip mine = newTrip("mana", "2026-12-05T08:00", 40);
        Trip other = newTrip("mana", "2026-12-05T09:00", 40);

        var ex = assertThrows(ValidationException.class, () -> service.create(user, mine.id(),
                CreateBookingRequest.fromJson(TripFixtures.bookingBody(other.stops().getFirst().id(), "Ana Pérez"))));
        assertTrue(ex.getErrors().containsKey("passengers.0.stopId"), ex.getErrors().toString());
    }

    @Test
    void aFullDepartureRefusesTheBookingAndOffersTheWaitlist() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 2);
        service.create(newUser(), trip.id(), request(trip, "Ana Pérez", "Juan Soto"));

        Caller late = newUser();
        assertThrows(BusinessRuleException.class, () -> service.create(late, trip.id(), request(trip, "Luz Díaz")));

        service.joinWaitlist(late, trip.id(), 1);
        assertTrue(service.waitlistedTripIds(late.userId()).contains(trip.id()));
        assertEquals(1, service.waitlist(companyId, trip.id()).size());
    }

    /** A group that doesn't fit is told how many seats are actually left, not just "it's full". */
    @Test
    void aGroupBiggerThanWhatIsLeftIsRefused() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 3);
        service.create(newUser(), trip.id(), request(trip, "Ana Pérez"));

        var ex = assertThrows(BusinessRuleException.class,
                () -> service.create(newUser(), trip.id(), request(trip, "Luz Díaz", "Eva Soto", "Max Rojas")));
        assertTrue(ex.getMessage().contains("2 asientos"), ex.getMessage());
    }

    /**
     * The reason {@code BookingRepository.insert} locks the trip row: two people taking the last
     * seat at the same time must not both get it.
     */
    @Test
    void twoPeopleTakingTheLastSeatAtOnceDoNotBothGetIt() throws Exception {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 1);
        Caller first = newUser();
        Caller second = newUser();

        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger booked = new AtomicInteger();
        for (Caller user : List.of(first, second)) {
            Thread.ofPlatform().start(() -> {
                try {
                    go.await();
                    service.create(user, trip.id(), request(trip, "Ana Pérez"));
                    booked.incrementAndGet();
                } catch (BusinessRuleException expected) {
                    // the one that lost the race
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        go.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "las reservas concurrentes no terminaron");

        assertEquals(1, booked.get(), "se vendió el mismo asiento dos veces");
        assertEquals(1, tripService.findById(companyId, trip.id()).seatsTaken());
    }

    /** Reaching the quorum is what turns a published departure into a confirmed one. */
    @Test
    void reachingTheQuorumConfirmsTheDeparture() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40, 2);
        assertEquals(TripStatus.PUBLISHED, trip.status());

        service.create(newUser(), trip.id(), request(trip, "Ana Pérez"));
        assertEquals(TripStatus.PUBLISHED, tripService.findById(companyId, trip.id()).status());

        service.create(newUser(), trip.id(), request(trip, "Juan Soto"));
        assertEquals(TripStatus.CONFIRMED, tripService.findById(companyId, trip.id()).status());
    }

    /** Cancelling frees seats, and whoever was waiting for them gets told. */
    @Test
    void cancellingASeatNotifiesTheWaitlist() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 1);
        Caller holder = newUser();
        Booking booking = service.create(holder, trip.id(), request(trip, "Ana Pérez"));

        Caller waiting = newUser();
        service.joinWaitlist(waiting, trip.id(), 1);
        String waitingEmail = userRepository.findById(waiting.userId()).orElseThrow().getEmail();

        service.cancel(holder.userId(), booking.id());

        var sent = emails.lastSentTo(waitingEmail);
        assertTrue(sent.subject().contains("Se liberó un cupo"), sent.subject());
        assertNotNull(service.waitlist(companyId, trip.id()).getFirst().notifiedAt());
    }

    @Test
    void bookingASeatTakesYouOffTheWaitlist() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 1);
        Caller holder = newUser();
        Booking booking = service.create(holder, trip.id(), request(trip, "Ana Pérez"));

        Caller waiting = newUser();
        service.joinWaitlist(waiting, trip.id(), 1);
        service.cancel(holder.userId(), booking.id());
        service.create(waiting, trip.id(), request(trip, "Luz Díaz"));

        assertFalse(service.waitlistedTripIds(waiting.userId()).contains(trip.id()));
    }

    @Test
    void thereIsNoWaitingForADepartureThatStillHasSeats() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40);
        assertThrows(BusinessRuleException.class, () -> service.joinWaitlist(newUser(), trip.id(), 1));
    }

    @Test
    void aDraftOrCancelledDepartureIsNeitherListedNorBookable() {
        Caller user = newUser();
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40);

        tripService.setStatus(companyId, trip.id(), TripStatus.CANCELLED);
        assertFalse(listedFor(user, trip.id()));
        assertThrows(BusinessRuleException.class, () -> service.create(user, trip.id(), request(trip, "Ana Pérez")));

        tripService.setStatus(companyId, trip.id(), TripStatus.DRAFT);
        assertFalse(listedFor(user, trip.id()));
        assertThrows(BusinessRuleException.class, () -> service.create(user, trip.id(), request(trip, "Ana Pérez")));
    }

    @Test
    void bookingsCloseAtTheDeadlineTheOperatorSet() {
        Map<String, Object> body = TripFixtures.tripBody(eventId("mana"), "2026-12-05T08:00", 40, 0);
        body.put("bookingDeadline", "2026-09-30T23:59");
        Trip trip = tripService.create(companyId, org.viajeseventos.dto.request.TripRequest.fromJson(body));

        var ex = assertThrows(BusinessRuleException.class,
                () -> service.create(newUser(), trip.id(), request(trip, "Ana Pérez")));
        assertTrue(ex.getMessage().contains("cerraron"), ex.getMessage());
    }

    @Test
    void endedEventsAreNotListedNorBookable() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 40);
        BookingService later = TripFixtures.bookingService(pool, "2030-01-01T12:00:00Z");
        Caller user = newUser();

        assertTrue(later.upcomingEvents(user).isEmpty());
        assertThrows(BusinessRuleException.class, () -> later.create(user, trip.id(), request(trip, "Ana Pérez")));
    }

    @Test
    void anEventStaysBookableThroughItsLastDayInChile() {
        // Oktoberfest runs 2026-10-09 to 2026-10-11. 2026-10-12 02:00 UTC is still the 11th in Chile.
        Trip trip = newTrip("oktoberfest-2026", "2026-10-09T08:00", 40);
        BookingService lastNight = TripFixtures.bookingService(pool, "2026-10-12T02:00:00Z");
        assertDoesNotThrow(() -> lastNight.create(newUser(), trip.id(), request(trip, "Ana Pérez")));
    }

    @Test
    void onlyTheOwnerCanCancelAndOnlyOnce() {
        Caller owner = newUser();
        Caller stranger = newUser();
        Trip trip = newTrip("deep-purple", "2026-11-20T08:00", 40);
        Booking booking = service.create(owner, trip.id(), request(trip, "Ana Pérez", "Juan Soto"));

        assertThrows(ForbiddenException.class, () -> service.cancel(stranger.userId(), booking.id()));

        Booking cancelled = service.cancel(owner.userId(), booking.id());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertNotNull(cancelled.cancelledAt());
        assertEquals(0, cancelled.seats());
        assertEquals(0, myCount(owner, trip.event().id()));
        assertThrows(BusinessRuleException.class, () -> service.cancel(owner.userId(), booking.id()));
    }

    /** A cancelled seat goes back into the pool, which is the whole reason to count seats live. */
    @Test
    void cancellingGivesTheSeatBack() {
        Trip trip = newTrip("mana", "2026-12-05T08:00", 1);
        Caller owner = newUser();
        Booking booking = service.create(owner, trip.id(), request(trip, "Ana Pérez"));
        assertEquals(0, tripService.findById(companyId, trip.id()).seatsLeft());

        service.cancel(owner.userId(), booking.id());
        assertEquals(1, tripService.findById(companyId, trip.id()).seatsLeft());
    }

    @Test
    void unknownDepartureIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.create(newUser(), -1,
                CreateBookingRequest.fromJson(TripFixtures.bookingBody(1, "Ana Pérez"))));
    }

    // ---- helpers ----

    private static Trip newTrip(String eventSlug, String departureAt, int seats) {
        return tripService.create(companyId, TripFixtures.trip(eventId(eventSlug), departureAt, seats));
    }

    private static Trip newTrip(String eventSlug, String departureAt, int seats, int minSeats) {
        return tripService.create(companyId, TripFixtures.trip(eventId(eventSlug), departureAt, seats, minSeats));
    }

    private static long eventId(String slug) {
        return eventRepository.findAll(companyId).stream().filter(e -> e.slug().equals(slug))
                .findFirst().orElseThrow().id();
    }

    private static CreateBookingRequest request(Trip trip, String... names) {
        return CreateBookingRequest.fromJson(TripFixtures.bookingBody(trip.stops().getFirst().id(), names));
    }

    private static boolean listedFor(Caller user, long tripId) {
        return service.upcomingEvents(user).stream()
                .flatMap(l -> l.trips().stream()).anyMatch(t -> t.id() == tripId);
    }

    private static int myCount(Caller user, long eventId) {
        return service.upcomingEvents(user).stream()
                .filter(l -> l.event().id() == eventId).findFirst().orElseThrow().myPassengerCount();
    }

    private static Caller newUser() {
        String unique = String.valueOf(System.nanoTime());
        User user = new User();
        user.setUsername("bk" + unique);
        user.setEmail("bk_" + unique + "@test.com");
        user.setPassword("x");
        user.setProfileId(clientProfileId);
        user.setCompanyId(companyId);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return new Caller(userRepository.insert(user).getId(), companyId);
    }
}
