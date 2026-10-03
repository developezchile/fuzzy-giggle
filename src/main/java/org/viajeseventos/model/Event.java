package org.viajeseventos.model;

import java.time.LocalDate;

/**
 * An out-of-town event people can book a bus trip to. {@code endDate} is null for single-day events.
 *
 * <p>{@code source} is the ticket vendor the event was imported from ("puntoticket",
 * "ticketmaster"), or null when somebody loaded it by hand — see {@code V16__event_imports.sql} and
 * the importer in {@code scraper/}. It's carried into the API responses so a listing can show where
 * an event came from.
 */
public record Event(long id, String slug, String name, String venue, String commune, String category,
                    String imageUrl, LocalDate startDate, LocalDate endDate, String sourceUrl,
                    boolean active, String source) {

    public LocalDate lastDay() {
        return endDate != null ? endDate : startDate;
    }

    /** Past its last day — no new bookings or cancellations. */
    public boolean hasEnded(LocalDate today) {
        return lastDay().isBefore(today);
    }
}
