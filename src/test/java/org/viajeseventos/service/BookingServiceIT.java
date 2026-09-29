package org.viajeseventos.service;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ForbiddenException;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingStatus;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.BookingRepository;
import org.viajeseventos.repository.EventRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Bookings against a real local Postgres (see {@link TestDb}), with "today" pinned by a fixed clock. */
class BookingServiceIT {

    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private static ConnectionPool pool;
    private static EventRepository eventRepository;
    private static BookingRepository bookingRepository;
    private static UserRepository userRepository;
    private static long clientProfileId;
    /** 2026-10-01 — before every seeded event. */
    private static BookingService service;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        eventRepository = new EventRepository(pool);
        bookingRepository = new BookingRepository(pool);
        userRepository = new UserRepository(pool);
        clientProfileId = new ProfileRepository(pool).findByCode(Profile.CLIENT).orElseThrow().getId();
        service = serviceAt("2026-10-01T12:00:00Z");
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void bookingStoresEveryPassengerAndCountsTowardsTheEvent() {
        long userId = newUser();
        long eventId = eventId("mana");

        Booking booking = service.create(userId, eventId, request("Ana Pérez", "Juan Soto"));

        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(2, booking.passengers().size());
        assertEquals("+56 9 1234 5678", booking.passengers().getFirst().phone());
        assertEquals(2, myCount(userId, eventId));
        assertEquals(List.of(booking.id()), service.myBookings(userId).stream().map(Booking::id).toList());
        assertTrue(service.allBookings(eventId).stream().anyMatch(b -> b.id() == booking.id()));
    }

    @Test
    void endedEventsAreNotListedNorBookable() {
        BookingService later = serviceAt("2030-01-01T12:00:00Z");
        long userId = newUser();

        assertTrue(later.upcomingEvents(userId).isEmpty());
        assertThrows(BusinessRuleException.class, () -> later.create(userId, eventId("mana"), request("Ana Pérez")));
    }

    @Test
    void anEventStaysBookableThroughItsLastDayInChile() {
        // Oktoberfest runs 2026-10-09 to 2026-10-11. 2026-10-12 02:00 UTC is still the 11th in Chile.
        BookingService lastNight = serviceAt("2026-10-12T02:00:00Z");
        assertDoesNotThrow(() -> lastNight.create(newUser(), eventId("oktoberfest-2026"), request("Ana Pérez")));
    }

    @Test
    void onlyTheOwnerCanCancelAndOnlyOnce() {
        long owner = newUser();
        long stranger = newUser();
        long eventId = eventId("deep-purple");
        Booking booking = service.create(owner, eventId, request("Ana Pérez", "Juan Soto"));

        assertThrows(ForbiddenException.class, () -> service.cancel(stranger, booking.id()));

        Booking cancelled = service.cancel(owner, booking.id());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertNotNull(cancelled.cancelledAt());
        assertEquals(0, myCount(owner, eventId));
        assertThrows(BusinessRuleException.class, () -> service.cancel(owner, booking.id()));
    }

    @Test
    void unknownEventIsNotFound() {
        assertThrows(org.viajeseventos.exception.ResourceNotFoundException.class,
                () -> service.create(newUser(), -1, request("Ana Pérez")));
    }

    // ---- helpers ----

    private static BookingService serviceAt(String instant) {
        return new BookingService(eventRepository, bookingRepository, Clock.fixed(Instant.parse(instant), CHILE));
    }

    private static long eventId(String slug) {
        return eventRepository.findAll().stream().filter(e -> e.slug().equals(slug)).findFirst().orElseThrow().id();
    }

    private static int myCount(long userId, long eventId) {
        return service.upcomingEvents(userId).stream()
                .filter(l -> l.event().id() == eventId).findFirst().orElseThrow().myPassengerCount();
    }

    private static long newUser() {
        String unique = String.valueOf(System.nanoTime());
        User user = new User();
        user.setUsername("bk" + unique);
        user.setEmail("bk_" + unique + "@test.com");
        user.setPassword("x");
        user.setProfileId(clientProfileId);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.insert(user).getId();
    }

    private static CreateBookingRequest request(String... names) {
        return CreateBookingRequest.fromJson(Map.of("passengers", java.util.Arrays.stream(names).map(name -> Map.<String, Object>of(
                "fullName", name,
                "phone", "912345678",
                "departurePlace", "Terminal Rodoviario",
                "departureTime", "07:30",
                "returnPlace", "Plaza de Armas")).toList()));
    }
}
