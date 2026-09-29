package org.viajeseventos.service;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.dto.request.EventRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.model.Event;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Event administration (EVENT_ADMIN) against a real local Postgres, and how it shows through to bookings. */
class EventServiceIT {

    private static ConnectionPool pool;
    private static EventService eventService;
    private static BookingService bookingService;
    private static UserRepository userRepository;
    private static long clientProfileId;
    private static long companyId;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        EventRepository eventRepository = new EventRepository(pool);
        eventService = new EventService(eventRepository);
        bookingService = new BookingService(eventRepository, new BookingRepository(pool),
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneId.of("America/Santiago")));
        userRepository = new UserRepository(pool);
        clientProfileId = new ProfileRepository(pool).findByCode(Profile.CLIENT).orElseThrow().getId();
        companyId = TestDb.seededCompanyId(pool);
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void createGeneratesAUniqueSlugFromTheName() {
        String name = "Ñandú & Café Tour " + System.nanoTime();
        Event first = eventService.create(companyId, request(name, "2026-12-01", null));
        Event second = eventService.create(companyId, request(name, "2026-12-02", null));

        assertTrue(first.slug().startsWith("nandu-cafe-tour-"), first.slug());
        assertEquals(first.slug() + "-2", second.slug());
    }

    @Test
    void updateChangesEverythingButTheSlug() {
        Event created = eventService.create(companyId, request("Original " + System.nanoTime(), "2026-12-01", null));

        Map<String, Object> body = body("Renombrado", "2026-12-05", "2026-12-06");
        body.put("active", false);
        Event updated = eventService.update(companyId, created.id(), EventRequest.fromJson(body));

        assertEquals(created.slug(), updated.slug());
        assertEquals("Renombrado", updated.name());
        assertEquals(LocalDate.parse("2026-12-06"), updated.endDate());
        assertFalse(updated.active());
    }

    @Test
    void deactivatedEventsAreHiddenFromClientsAndNotBookable() {
        Event event = eventService.create(companyId, request("Inactivo " + System.nanoTime(), "2026-12-01", null));
        Caller user = newUser();
        assertTrue(listedFor(user, event.id()));

        Map<String, Object> body = body(event.name(), "2026-12-01", null);
        body.put("active", false);
        eventService.update(companyId, event.id(), EventRequest.fromJson(body));

        assertFalse(listedFor(user, event.id()));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(user, event.id(), booking()));
    }

    @Test
    void onlyEventsWithoutBookingsCanBeDeleted() {
        Event unused = eventService.create(companyId, request("Sin reservas " + System.nanoTime(), "2026-12-01", null));
        eventService.delete(companyId, unused.id());
        assertThrows(ResourceNotFoundException.class, () -> eventService.findById(companyId, unused.id()));

        Event booked = eventService.create(companyId, request("Con reservas " + System.nanoTime(), "2026-12-01", null));
        Caller user = newUser();
        var b = bookingService.create(user, booked.id(), booking());
        bookingService.cancel(user.userId(), b.id());
        // Even a cancelled booking is history worth keeping.
        assertThrows(BusinessRuleException.class, () -> eventService.delete(companyId, booked.id()));
    }

    @Test
    void anotherCompanysEventsAreInvisibleToItsAdminsAndClients() {
        Event mine = eventService.create(companyId, request("Solo mío " + System.nanoTime(), "2026-12-01", null));
        Caller myClient = newUser();
        bookingService.create(myClient, mine.id(), booking());

        long other = TestDb.newCompanyId(pool);
        Caller otherClient = new Caller(myClient.userId(), other);
        assertThrows(ResourceNotFoundException.class, () -> eventService.findById(other, mine.id()));
        assertThrows(ResourceNotFoundException.class, () -> eventService.update(other, mine.id(),
                request("Hackeado", "2026-12-01", null)));
        assertThrows(ResourceNotFoundException.class, () -> eventService.delete(other, mine.id()));
        assertTrue(eventService.findAll(other).isEmpty());
        assertFalse(listedFor(otherClient, mine.id()));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(otherClient, mine.id(), booking()));
        assertTrue(bookingService.allBookings(other, null).isEmpty());
        assertTrue(bookingService.allBookings(other, mine.id()).isEmpty());
        assertTrue(bookingService.allEvents(other).isEmpty());
    }

    @Test
    void slugsOnlyNeedToBeUniqueWithinACompany() {
        String name = "Compartido " + System.nanoTime();
        Event mine = eventService.create(companyId, request(name, "2026-12-01", null));
        Event theirs = eventService.create(TestDb.newCompanyId(pool), request(name, "2026-12-01", null));
        assertEquals(mine.slug(), theirs.slug());
    }

    @Test
    void datesAreValidated() {
        assertThrows(ValidationException.class, () -> request("X", "2026-12-05", "2026-12-01"));
        assertThrows(ValidationException.class, () -> EventRequest.fromJson(Map.of("name", "X", "venue", "V", "commune", "C")));
        assertThrows(ValidationException.class, () -> EventRequest.fromJson(Map.of(
                "name", "X", "venue", "V", "commune", "C", "startDate", "2026-12-01", "imageUrl", "ftp://x/y.jpg")));
        // Same start and end is a one-day event.
        assertNull(request("X", "2026-12-01", "2026-12-01").endDate);
    }

    // ---- helpers ----

    private static Map<String, Object> body(String name, String start, String end) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("venue", "Movistar Arena");
        body.put("commune", "Santiago");
        body.put("category", "Rock");
        body.put("imageUrl", "https://static.ptocdn.net/images/eventos/x.jpg");
        body.put("startDate", start);
        if (end != null) body.put("endDate", end);
        return body;
    }

    private static EventRequest request(String name, String start, String end) {
        return EventRequest.fromJson(body(name, start, end));
    }

    private static boolean listedFor(Caller user, long eventId) {
        return bookingService.upcomingEvents(user).stream().anyMatch(l -> l.event().id() == eventId);
    }

    private static Caller newUser() {
        String unique = String.valueOf(System.nanoTime());
        User user = new User();
        user.setUsername("ev" + unique);
        user.setEmail("ev_" + unique + "@test.com");
        user.setPassword("x");
        user.setProfileId(clientProfileId);
        user.setCompanyId(companyId);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return new Caller(userRepository.insert(user).getId(), companyId);
    }

    private static CreateBookingRequest booking() {
        return CreateBookingRequest.fromJson(Map.of("passengers", List.of(Map.of(
                "fullName", "Ana Pérez", "phone", "912345678", "departurePlace", "Terminal",
                "departureTime", "08:00", "returnPlace", "Terminal"))));
    }
}
