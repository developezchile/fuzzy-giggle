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
import org.viajeseventos.security.Caller;
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
    private static long companyId;
    /** 2026-10-01 — before every seeded event. */
    private static BookingService service;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        eventRepository = new EventRepository(pool);
        bookingRepository = new BookingRepository(pool);
        userRepository = new UserRepository(pool);
        clientProfileId = new ProfileRepository(pool).findByCode(Profile.CLIENT).orElseThrow().getId();
        companyId = TestDb.seededCompanyId(pool);
        service = serviceAt("2026-10-01T12:00:00Z");
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void bookingStoresEveryPassengerAndCountsTowardsTheEvent() {
        Caller user = newUser();
        long eventId = eventId("mana");

        Booking booking = service.create(user, eventId, request("Ana Pérez", "Juan Soto"));

        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(2, booking.passengers().size());
        assertEquals("+56 9 1234 5678", booking.passengers().getFirst().phone());
        assertEquals(2, myCount(user, eventId));
        assertEquals(List.of(booking.id()), service.myBookings(user.userId()).stream().map(Booking::id).toList());
        assertTrue(service.allBookings(companyId, eventId).stream().anyMatch(b -> b.id() == booking.id()));
    }

    @Test
    void endedEventsAreNotListedNorBookable() {
        BookingService later = serviceAt("2030-01-01T12:00:00Z");
        Caller user = newUser();

        assertTrue(later.upcomingEvents(user).isEmpty());
        assertThrows(BusinessRuleException.class, () -> later.create(user, eventId("mana"), request("Ana Pérez")));
    }

    @Test
    void anEventStaysBookableThroughItsLastDayInChile() {
        // Oktoberfest runs 2026-10-09 to 2026-10-11. 2026-10-12 02:00 UTC is still the 11th in Chile.
        BookingService lastNight = serviceAt("2026-10-12T02:00:00Z");
        assertDoesNotThrow(() -> lastNight.create(newUser(), eventId("oktoberfest-2026"), request("Ana Pérez")));
    }

    @Test
    void onlyTheOwnerCanCancelAndOnlyOnce() {
        Caller owner = newUser();
        Caller stranger = newUser();
        long eventId = eventId("deep-purple");
        Booking booking = service.create(owner, eventId, request("Ana Pérez", "Juan Soto"));

        assertThrows(ForbiddenException.class, () -> service.cancel(stranger.userId(), booking.id()));

        Booking cancelled = service.cancel(owner.userId(), booking.id());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertNotNull(cancelled.cancelledAt());
        assertEquals(0, myCount(owner, eventId));
        assertThrows(BusinessRuleException.class, () -> service.cancel(owner.userId(), booking.id()));
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
        return eventRepository.findAll(companyId).stream().filter(e -> e.slug().equals(slug)).findFirst().orElseThrow().id();
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

    private static CreateBookingRequest request(String... names) {
        return CreateBookingRequest.fromJson(Map.of("passengers", java.util.Arrays.stream(names).map(name -> Map.<String, Object>of(
                "fullName", name,
                "phone", "912345678",
                "departurePlace", "Terminal Rodoviario",
                "departureTime", "07:30",
                "returnPlace", "Plaza de Armas")).toList()));
    }
}
