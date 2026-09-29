package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingPassenger;
import org.viajeseventos.model.Event;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class BookingResponse {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private BookingResponse() {
    }

    public static Map<String, Object> event(Event event) {
        Map<String, Object> map = Json.obj();
        map.put("id", event.id());
        map.put("slug", event.slug());
        map.put("name", event.name());
        map.put("venue", event.venue());
        map.put("commune", event.commune());
        map.put("category", event.category());
        map.put("imageUrl", event.imageUrl());
        map.put("startDate", event.startDate());
        map.put("endDate", event.endDate());
        map.put("sourceUrl", event.sourceUrl());
        return map;
    }

    public static Map<String, Object> from(Booking booking) {
        Map<String, Object> map = Json.obj();
        map.put("id", booking.id());
        map.put("status", booking.status().name());
        map.put("createdAt", booking.createdAt());
        map.put("cancelledAt", booking.cancelledAt());
        map.put("event", event(booking.event()));

        Map<String, Object> bookedBy = Json.obj();
        bookedBy.put("id", booking.userId());
        bookedBy.put("name", booking.userName());
        bookedBy.put("email", booking.userEmail());
        map.put("bookedBy", bookedBy);

        map.put("passengers", booking.passengers().stream().map(BookingResponse::passenger).toList());
        return map;
    }

    private static Map<String, Object> passenger(BookingPassenger p) {
        Map<String, Object> map = Json.obj();
        map.put("position", p.position());
        map.put("fullName", p.fullName());
        map.put("phone", p.phone());
        map.put("departurePlace", p.departurePlace());
        map.put("departureTime", p.departureTime().format(HH_MM));
        map.put("returnPlace", p.returnPlace());
        return map;
    }
}
