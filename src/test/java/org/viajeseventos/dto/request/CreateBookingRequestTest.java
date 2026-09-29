package org.viajeseventos.dto.request;

import org.viajeseventos.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreateBookingRequestTest {

    private static Map<String, Object> passenger(String fullName, String phone) {
        Map<String, Object> p = new HashMap<>();
        p.put("fullName", fullName);
        p.put("phone", phone);
        p.put("departurePlace", "Terminal Rodoviario");
        p.put("departureTime", "07:30");
        p.put("returnPlace", "Plaza de Armas");
        return p;
    }

    @Test
    void acceptsValidPassengersAndNormalizesThem() {
        var request = CreateBookingRequest.fromJson(Map.of("passengers", List.of(
                passenger("  Ana   Pérez ", "912345678"),
                passenger("Juan Soto", "+56 9 8765 4321"))));

        assertEquals(2, request.passengers.size());
        var first = request.passengers.getFirst();
        assertEquals(1, first.position());
        assertEquals("Ana Pérez", first.fullName());
        assertEquals("+56 9 1234 5678", first.phone());
        assertEquals(LocalTime.of(7, 30), first.departureTime());
        assertEquals("+56 9 8765 4321", request.passengers.get(1).phone());
    }

    @Test
    void reportsErrorsPerPassengerField() {
        Map<String, Object> bad = passenger("Ana", "12345");
        bad.put("departureTime", "25:99");
        bad.remove("returnPlace");

        ValidationException ex = assertThrows(ValidationException.class, () -> CreateBookingRequest.fromJson(
                Map.of("passengers", List.of(passenger("Ana Pérez", "912345678"), bad))));

        var errors = ex.getErrors();
        assertEquals(4, errors.size(), errors.toString());
        assertTrue(errors.containsKey("passengers.1.fullName"));
        assertTrue(errors.containsKey("passengers.1.phone"));
        assertTrue(errors.containsKey("passengers.1.departureTime"));
        assertTrue(errors.containsKey("passengers.1.returnPlace"));
    }

    @Test
    void requiresAtLeastOneAndAtMostTwentyPassengers() {
        assertThrows(ValidationException.class, () -> CreateBookingRequest.fromJson(Map.of("passengers", List.of())));
        assertThrows(ValidationException.class, () -> CreateBookingRequest.fromJson(Map.of()));

        List<Object> many = new ArrayList<>();
        for (int i = 0; i < CreateBookingRequest.MAX_PASSENGERS + 1; i++) many.add(passenger("Ana Pérez", "912345678"));
        assertThrows(ValidationException.class, () -> CreateBookingRequest.fromJson(Map.of("passengers", many)));
    }
}
