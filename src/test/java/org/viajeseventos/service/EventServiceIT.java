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

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        EventRepository eventRepository = new EventRepository(pool);
        eventService = new EventService(eventRepository);
        bookingService = new BookingService(eventRepository, new BookingRepository(pool),
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneId.of("America/Santiago")));
        userRepository = new UserRepository(pool);
        clientProfileId = new ProfileRepository(pool).findByCode(Profile.CLIENT).orElseThrow().getId();
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void createGeneratesAUniqueSlugFromTheName() {
        String name = "Ñandú & Café Tour " + System.nanoTime();
        Event first = eventService.create(request(name, "2026-12-01", null));
        Event second = eventService.create(request(name, "2026-12-02", null));

        assertTrue(first.slug().startsWith("nandu-cafe-tour-"), first.slug());
        assertEquals(first.slug() + "-2", second.slug());
    }

    @Test
    void updateChangesEverythingButTheSlug() {
        Event created = eventService.create(request("Original " + System.nanoTime(), "2026-12-01", null));

        Map<String, Object> body = body("Renombrado", "2026-12-05", "2026-12-06");
        body.put("active", false);
        Event updated = eventService.update(created.id(), EventRequest.fromJson(body));

        assertEquals(created.slug(), updated.slug());
        assertEquals("Renombrado", updated.name());
        assertEquals(LocalDate.parse("2026-12-06"), updated.endDate());
        assertFalse(updated.active());
    }

    @Test
    void deactivatedEventsAreHiddenFromClientsAndNotBookable() {
        Event event = eventService.create(request("Inactivo " + System.nanoTime(), "2026-12-01", null));
        long userId = newUser();
        assertTrue(listedFor(userId, event.id()));

        Map<String, Object> body = body(event.name(), "2026-12-01", null);
        body.put("active", false);
        eventService.update(event.id(), EventRequest.fromJson(body));

        assertFalse(listedFor(userId, event.id()));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(userId, event.id(), booking()));
    }

    @Test
    void onlyEventsWithoutBookingsCanBeDeleted() {
        Event unused = eventService.create(request("Sin reservas " + System.nanoTime(), "2026-12-01", null));
        eventService.delete(unused.id());
        assertThrows(ResourceNotFoundException.class, () -> eventService.findById(unused.id()));

        Event booked = eventService.create(request("Con reservas " + System.nanoTime(), "2026-12-01", null));
        long userId = newUser();
        var b = bookingService.create(userId, booked.id(), booking());
        bookingService.cancel(userId, b.id());
        // Even a cancelled booking is history worth keeping.
        assertThrows(BusinessRuleException.class, () -> eventService.delete(booked.id()));
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

    private static boolean listedFor(long userId, long eventId) {
        return bookingService.upcomingEvents(userId).stream().anyMatch(l -> l.event().id() == eventId);
    }

    private static long newUser() {
        String unique = String.valueOf(System.nanoTime());
        User user = new User();
        user.setUsername("ev" + unique);
        user.setEmail("ev_" + unique + "@test.com");
        user.setPassword("x");
        user.setProfileId(clientProfileId);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.insert(user).getId();
    }

    private static CreateBookingRequest booking() {
        return CreateBookingRequest.fromJson(Map.of("passengers", List.of(Map.of(
                "fullName", "Ana Pérez", "phone", "912345678", "departurePlace", "Terminal",
                "departureTime", "08:00", "returnPlace", "Terminal"))));
    }
}
