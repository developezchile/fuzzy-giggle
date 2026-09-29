package org.viajeseventos.dto.request;

import org.viajeseventos.model.BookingPassenger;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.viajeseventos.validation.Validate.*;

/**
 * Passengers for one booking, as sent by the "Viajar" modal. Same rules as the UI's
 * {@code lib/passengers.ts}; errors are keyed {@code passengers.<index>.<field>} so the modal can
 * put each one under its field.
 */
public final class CreateBookingRequest {

    public static final int MAX_PASSENGERS = 20;

    /** Chilean mobile: 9 digits starting with 9, with or without the 56 country code. */
    private static final Pattern CHILE_MOBILE = Pattern.compile("^(56)?(9\\d{8})$");

    public final List<BookingPassenger> passengers;

    private CreateBookingRequest(List<BookingPassenger> passengers) {
        this.passengers = passengers;
    }

    public static CreateBookingRequest fromJson(Map<String, Object> json) {
        var errors = newErrors();
        List<BookingPassenger> passengers = new ArrayList<>();

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
            String departurePlace = optStr(item, "departurePlace");
            String departureTimeRaw = optStr(item, "departureTime");
            String returnPlace = optStr(item, "returnPlace");

            if (fullName == null) errors.put(prefix + "fullName", "Ingresa el nombre completo");
            else if (fullName.split(" ").length < 2) errors.put(prefix + "fullName", "Ingresa nombre y apellido");
            else maxLength(errors, prefix + "fullName", fullName, 200);

            String normalizedPhone = normalizePhone(phone);
            if (phone == null) errors.put(prefix + "phone", "Ingresa el número de celular");
            else if (normalizedPhone == null) errors.put(prefix + "phone", "Ingresa un celular válido (+56 9 1234 5678)");

            if (departurePlace == null) errors.put(prefix + "departurePlace", "Ingresa el lugar de salida");
            else maxLength(errors, prefix + "departurePlace", departurePlace, 200);

            LocalTime departureTime = null;
            if (departureTimeRaw == null) {
                errors.put(prefix + "departureTime", "Ingresa la hora de salida");
            } else {
                try {
                    departureTime = LocalTime.parse(departureTimeRaw);
                } catch (DateTimeParseException e) {
                    errors.put(prefix + "departureTime", "debe tener el formato HH:MM");
                }
            }

            if (returnPlace == null) errors.put(prefix + "returnPlace", "Ingresa el lugar de retorno");
            else maxLength(errors, prefix + "returnPlace", returnPlace, 200);

            passengers.add(new BookingPassenger(i + 1, fullName, normalizedPhone, departurePlace, departureTime, returnPlace));
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
