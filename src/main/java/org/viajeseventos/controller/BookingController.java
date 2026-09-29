package org.viajeseventos.controller;

import org.viajeseventos.dto.request.CreateBookingRequest;
import org.viajeseventos.dto.response.BookingResponse;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.BookingService;

import java.util.Map;

/**
 * Events and bookings. The caller's own side lives in the EVENTS module; the operator's view of
 * every booking is the BOOKINGS module.
 */
public final class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public void register(Router router) {
        router.get("/events", AppModule.EVENTS, this::upcomingEvents);
        router.post("/events/{id}/bookings", AppModule.MY_BOOKINGS, this::create);
        router.get("/bookings/me", AppModule.MY_BOOKINGS, this::mine);
        router.post("/bookings/{id}/cancel", AppModule.MY_BOOKINGS, this::cancel);

        router.get("/admin/bookings/events", AppModule.BOOKINGS, this::allEvents);
        router.get("/admin/bookings", AppModule.BOOKINGS, this::allBookings);
    }

    private Response upcomingEvents(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("events", bookingService.upcomingEvents(ctx.caller()).stream().map(listing -> {
            Map<String, Object> event = BookingResponse.event(listing.event());
            event.put("myPassengerCount", listing.myPassengerCount());
            return event;
        }).toList());
        return Response.ok(body);
    }

    private Response create(RequestContext ctx) {
        long eventId = ctx.pathParamLong("id");
        var booking = bookingService.create(ctx.caller(), eventId, CreateBookingRequest.fromJson(ctx.jsonBody()));
        return Response.created(BookingResponse.from(booking));
    }

    private Response mine(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("bookings", bookingService.myBookings(ctx.caller().userId()).stream().map(BookingResponse::from).toList());
        return Response.ok(body);
    }

    private Response cancel(RequestContext ctx) {
        return Response.ok(BookingResponse.from(bookingService.cancel(ctx.caller().userId(), ctx.pathParamLong("id"))));
    }

    private Response allEvents(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("events", bookingService.allEvents(ctx.caller().companyId()).stream().map(BookingResponse::event).toList());
        return Response.ok(body);
    }

    private Response allBookings(RequestContext ctx) {
        String raw = ctx.queryParam("eventId");
        Long eventId = null;
        if (raw != null && !raw.isBlank()) {
            try {
                eventId = Long.valueOf(raw);
            } catch (NumberFormatException e) {
                throw BusinessRuleException.badRequest("El evento '" + raw + "' no es válido");
            }
        }
        Map<String, Object> body = Json.obj();
        body.put("bookings", bookingService.allBookings(ctx.caller().companyId(), eventId).stream().map(BookingResponse::from).toList());
        return Response.ok(body);
    }
}
