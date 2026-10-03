package org.viajeseventos.dto.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.viajeseventos.validation.Validate.*;

/**
 * Passengers for one booking, as sent by the "Viajar" modal. Same rules as the UI's
 * {@code lib/passengers.ts}; errors are keyed {@code passengers.<index>.<field>} so the modal can
 * put each one under its field.
 *
 * <p>Since V8 a passenger picks one of the trip's stops instead of typing where and when they're
 * picked up. The stop has to belong to the trip being booked, which only
 * {@code BookingService} can tell, so that check happens there and reports itself under the same
 * {@code passengers.<index>.stopId} key.
 */
public final class CreateBookingRequest {

    public static final int MAX_PASSENGERS = 20;

    /** Chilean mobile: 9 digits starting with 9, with or without the 56 country code. */
    private static final Pattern CHILE_MOBILE = Pattern.compile("^(56)?(9\\d{8})$");

    /** A passenger as submitted, before the trip's stop is resolved into a place and a time. */
    public record PassengerInput(int position, String fullName, String phone, Long stopId, String returnPlace) {
    }

    public final List<PassengerInput> passengers;

    private CreateBookingRequest(List<PassengerInput> passengers) {
        this.passengers = passengers;
    }

    public static CreateBookingRequest fromJson(Map<String, Object> json) {
        var errors = newErrors();
        List<PassengerInput> passengers = new ArrayList<>();

        if (!(json.get("passengers") instanceof List<?> items) || items.isEmpty()) {
            errors.put("passengers", "debe incluir al menos un pasajero");
            check(errors);
            return null; // unreachable — check() throws
        }
        if (items.size() > MAX_PASSENGERS) {
            errors.put("passengers", "no puede superar los " + MAX_PASSENGERS + " pasajeros por reserva");
            check(errors);
        }

        for (int i = 0; i < items.size(); i++) {
            String prefix = "passengers." + i + ".";
            if (!(items.get(i) instanceof Map<?, ?> raw)) {
                errors.put("passengers." + i, "debe ser un objeto");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) raw;

            String fullName = collapseSpaces(optStr(item, "fullName"));
            String phone = optStr(item, "phone");
            String returnPlace = optStr(item, "returnPlace");
            Long stopId = longVal(errors, item, "stopId");

            if (fullName == null) errors.put(prefix + "fullName", "Ingresa el nombre completo");
            else if (fullName.split(" ").length < 2) errors.put(prefix + "fullName", "Ingresa nombre y apellido");
            else maxLength(errors, prefix + "fullName", fullName, 200);

            String normalizedPhone = normalizePhone(phone);
            if (phone == null) errors.put(prefix + "phone", "Ingresa el número de celular");
            else if (normalizedPhone == null) errors.put(prefix + "phone", "Ingresa un celular válido (+56 9 1234 5678)");

            if (stopId == null) errors.put(prefix + "stopId", "Elige dónde te subes");

            if (returnPlace == null) errors.put(prefix + "returnPlace", "Ingresa el lugar de retorno");
            else maxLength(errors, prefix + "returnPlace", returnPlace, 200);

            passengers.add(new PassengerInput(i + 1, fullName, normalizedPhone, stopId, returnPlace));
        }
        check(errors);

        return new CreateBookingRequest(List.copyOf(passengers));
    }

    private static String collapseSpaces(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ");
    }

    /** Stored in one canonical shape ({@code +56 9 1234 5678}) regardless of how it was typed. */
    static String normalizePhone(String phone) {
        if (phone == null) return null;
        var m = CHILE_MOBILE.matcher(phone.replaceAll("\\D", ""));
        if (!m.matches()) return null;
        String local = m.group(2);
        return "+56 " + local.charAt(0) + " " + local.substring(1, 5) + " " + local.substring(5);
    }
}
