package org.viajeseventos.controller;

import org.viajeseventos.dto.request.EventRequest;
import org.viajeseventos.dto.response.BookingResponse;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Event;
import org.viajeseventos.service.EventService;

import java.util.Map;

/** Event administration — every route requires the EVENT_ADMIN module. */
public final class EventAdminController {

    private final EventService eventService;

    public EventAdminController(EventService eventService) {
        this.eventService = eventService;
    }

    public void register(Router router) {
        router.get("/admin/events", AppModule.EVENT_ADMIN, this::list);
        router.get("/admin/events/{id}", AppModule.EVENT_ADMIN, this::get);
        router.post("/admin/events", AppModule.EVENT_ADMIN, this::create);
        router.put("/admin/events/{id}", AppModule.EVENT_ADMIN, this::update);
        router.delete("/admin/events/{id}", AppModule.EVENT_ADMIN, this::delete);
    }

    private Response list(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("events", eventService.findAll().stream().map(listing -> {
            Map<String, Object> event = adminEvent(listing.event());
            event.put("bookingCount", listing.bookingCount());
            event.put("confirmedPassengers", listing.confirmedPassengers());
            return event;
        }).toList());
        return Response.ok(body);
    }

    private Response get(RequestContext ctx) {
        return Response.ok(adminEvent(eventService.findById(ctx.pathParamLong("id"))));
    }

    private Response create(RequestContext ctx) {
        return Response.created(adminEvent(eventService.create(EventRequest.fromJson(ctx.jsonBody()))));
    }

    private Response update(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        return Response.ok(adminEvent(eventService.update(id, EventRequest.fromJson(ctx.jsonBody()))));
    }

    private Response delete(RequestContext ctx) {
        eventService.delete(ctx.pathParamLong("id"));
        return Response.noContent();
    }

    private static Map<String, Object> adminEvent(Event event) {
        Map<String, Object> map = BookingResponse.event(event);
        map.put("active", event.active());
        return map;
    }
}
