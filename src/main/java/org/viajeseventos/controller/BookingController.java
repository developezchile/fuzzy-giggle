package org.viajeseventos.controller;

import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.dto.response.BookingResponse;
import org.viajeseventos.dto.response.TripResponse;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.BookingService;

import java.util.Map;
import java.util.Set;

/**
 * Events, departures and bookings from the client's side (EVENTS to look, MY_BOOKINGS to book),
 * plus the operator's view of every booking (BOOKINGS).
 */
public final class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public void register(Router router) {
        router.get("/events", AppModule.EVENTS, this::upcomingEvents);
        router.get("/trips/{id}", AppModule.EVENTS, this::trip);
        router.post("/trips/{id}/bookings", AppModule.MY_BOOKINGS, this::create);
        router.post("/trips/{id}/waitlist", AppModule.MY_BOOKINGS, this::joinWaitlist);
        router.delete("/trips/{id}/waitlist", AppModule.MY_BOOKINGS, this::leaveWaitlist);
        router.get("/bookings/me", AppModule.MY_BOOKINGS, this::mine);
        router.post("/bookings/{id}/cancel", AppModule.MY_BOOKINGS, this::cancel);

        router.get("/admin/bookings/trips", AppModule.BOOKINGS, this::allTrips);
        router.get("/admin/bookings", AppModule.BOOKINGS, this::allBookings);
    }

    /** Each upcoming event with the departures a client can still take — full ones included. */
    private Response upcomingEvents(RequestContext ctx) {
        Set<Long> waiting = bookingService.waitlistedTripIds(ctx.caller().userId());
        Map<String, Object> body = Json.obj();
        body.put("events", bookingService.upcomingEvents(ctx.caller()).stream().map(listing -> {
            Map<String, Object> event = BookingResponse.event(listing.event());
            event.put("myPassengerCount", listing.myPassengerCount());
            event.put("trips", listing.trips().stream().map(trip -> {
                Map<String, Object> map = TripResponse.from(trip);
                map.put("onWaitlist", waiting.contains(trip.id()));
                return map;
            }).toList());
            return event;
        }).toList());
        return Response.ok(body);
    }

    private Response trip(RequestContext ctx) {
        return Response.ok(TripResponse.from(bookingService.trip(ctx.caller(), ctx.pathParamLong("id"))));
    }

    private Response create(RequestContext ctx) {
        long tripId = ctx.pathParamLong("id");
        var booking = bookingService.create(ctx.caller(), tripId, CreateBookingRequest.fromJson(ctx.jsonBody()));
        return Response.created(BookingResponse.from(booking));
    }

    private Response joinWaitlist(RequestContext ctx) {
        Object seats = ctx.jsonBody().get("seats");
        int wanted = seats instanceof Number n ? n.intValue() : 1;
        bookingService.joinWaitlist(ctx.caller(), ctx.pathParamLong("id"), wanted);
        Map<String, Object> body = Json.obj();
        body.put("message", "Te anotamos en la lista de espera. Te avisamos por correo si se libera un cupo.");
        return Response.ok(body);
    }

    private Response leaveWaitlist(RequestContext ctx) {
        bookingService.leaveWaitlist(ctx.caller(), ctx.pathParamLong("id"));
        return Response.noContent();
    }

    private Response mine(RequestContext ctx) {
        long userId = ctx.caller().userId();
        Map<String, Object> body = Json.obj();
        body.put("bookings", bookingService.myBookings(userId).stream().map(BookingResponse::from).toList());
        body.put("waitlistedTripIds", bookingService.waitlistedTripIds(userId).stream().sorted().toList());
        return Response.ok(body);
    }

    private Response cancel(RequestContext ctx) {
        return Response.ok(BookingResponse.from(bookingService.cancel(ctx.caller().userId(), ctx.pathParamLong("id"))));
    }

    /** The departures the operator can filter their bookings by. */
    private Response allTrips(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("trips", bookingService.allTrips(ctx.caller().companyId()).stream().map(TripResponse::from).toList());
        return Response.ok(body);
    }

    private Response allBookings(RequestContext ctx) {
        Long tripId = TripAdminController.optionalLong(ctx, "tripId");
        Long eventId = TripAdminController.optionalLong(ctx, "eventId");
        Map<String, Object> body = Json.obj();
        body.put("bookings", bookingService.allBookings(ctx.caller().companyId(), tripId, eventId).stream()
                .map(BookingResponse::from).toList());
        return Response.ok(body);
    }
}
