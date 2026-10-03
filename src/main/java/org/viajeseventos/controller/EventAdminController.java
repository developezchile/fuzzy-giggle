package org.viajeseventos.controller;

import org.viajeseventos.dto.request.EventRequest;
import org.viajeseventos.dto.response.BookingResponse;
import org.viajeseventos.exception.BusinessRuleException;
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
        router.patch("/admin/events/{id}", AppModule.EVENT_ADMIN, this::setActive);
        router.delete("/admin/events/{id}", AppModule.EVENT_ADMIN, this::delete);
    }

    private Response list(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("events", eventService.findAll(ctx.caller().companyId()).stream().map(listing -> {
            Map<String, Object> event = adminEvent(listing.event());
            event.put("tripCount", listing.tripCount());
            event.put("bookingCount", listing.bookingCount());
            event.put("tripsWithoutRoute", listing.tripsWithoutRoute());
            event.put("confirmedPassengers", listing.confirmedPassengers());
            return event;
        }).toList());
        return Response.ok(body);
    }

    private Response get(RequestContext ctx) {
        return Response.ok(adminEvent(eventService.findById(ctx.caller().companyId(), ctx.pathParamLong("id"))));
    }

    private Response create(RequestContext ctx) {
        return Response.created(adminEvent(eventService.create(ctx.caller().companyId(), EventRequest.fromJson(ctx.jsonBody()))));
    }

    private Response update(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        return Response.ok(adminEvent(eventService.update(ctx.caller().companyId(), id, EventRequest.fromJson(ctx.jsonBody()))));
    }

    /**
     * Activar o desactivar, sin mandar el evento entero. Un PUT para esto obligaría al cliente a
     * reenviar todos los campos, y cualquiera que olvidara uno lo borraría sin querer.
     */
    private Response setActive(RequestContext ctx) {
        Map<String, Object> json = ctx.jsonBody();
        if (!(json.get("active") instanceof Boolean active)) {
            throw BusinessRuleException.badRequest("Indica si el evento queda activo o inactivo");
        }
        return Response.ok(adminEvent(eventService.setActive(ctx.caller().companyId(), ctx.pathParamLong("id"), active)));
    }

    private Response delete(RequestContext ctx) {
        eventService.delete(ctx.caller().companyId(), ctx.pathParamLong("id"));
        return Response.noContent();
    }

    private static Map<String, Object> adminEvent(Event event) {
        Map<String, Object> map = BookingResponse.event(event);
        map.put("active", event.active());
        return map;
    }
}
