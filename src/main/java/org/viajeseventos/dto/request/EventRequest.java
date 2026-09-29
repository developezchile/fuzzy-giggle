package org.viajeseventos.dto.request;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/** Create/update body for the EVENT_ADMIN module. */
public final class EventRequest {

    public final String name;
    public final String venue;
    public final String commune;
    public final String category;
    public final String imageUrl;
    public final LocalDate startDate;
    public final LocalDate endDate;
    public final String sourceUrl;
    public final boolean active;

    private EventRequest(String name, String venue, String commune, String category, String imageUrl,
                         LocalDate startDate, LocalDate endDate, String sourceUrl, boolean active) {
        this.name = name;
        this.venue = venue;
        this.commune = commune;
        this.category = category;
        this.imageUrl = imageUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sourceUrl = sourceUrl;
        this.active = active;
    }

    public static EventRequest fromJson(Map<String, Object> json) {
        String name = optStr(json, "name");
        String venue = optStr(json, "venue");
        String commune = optStr(json, "commune");
        String category = optStr(json, "category");
        String imageUrl = optStr(json, "imageUrl");
        String sourceUrl = optStr(json, "sourceUrl");

        var errors = newErrors();
        notBlank(errors, "name", name);
        maxLength(errors, "name", name, 255);
        notBlank(errors, "venue", venue);
        maxLength(errors, "venue", venue, 255);
        notBlank(errors, "commune", commune);
        maxLength(errors, "commune", commune, 120);
        maxLength(errors, "category", category, 120);
        httpUrl(errors, "imageUrl", imageUrl);
        httpUrl(errors, "sourceUrl", sourceUrl);

        LocalDate startDate = dateVal(errors, "startDate", optStr(json, "startDate"));
        notNull(errors, "startDate", startDate);
        LocalDate endDate = dateVal(errors, "endDate", optStr(json, "endDate"));
        if (startDate != null && endDate != null) {
            if (endDate.isBefore(startDate)) errors.put("endDate", "no puede ser anterior a la fecha de inicio");
            // A one-day event is stored without an end date.
            if (endDate.equals(startDate)) endDate = null;
        }
        Boolean active = boolVal(errors, json, "active");
        check(errors);

        return new EventRequest(name, venue, commune, category, imageUrl, startDate, endDate, sourceUrl,
                active == null || active);
    }

    private static void httpUrl(Map<String, String> errors, String field, String value) {
        if (value == null) return;
        maxLength(errors, field, value, 500);
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                errors.putIfAbsent(field, "debe ser una URL http(s) válida");
            }
        } catch (IllegalArgumentException e) {
            errors.putIfAbsent(field, "debe ser una URL http(s) válida");
        }
    }
}
