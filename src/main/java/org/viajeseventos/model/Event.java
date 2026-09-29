package org.viajeseventos.model;

import java.time.LocalDate;

/** An out-of-town event people can book a bus trip to. {@code endDate} is null for single-day events. */
public record Event(long id, String slug, String name, String venue, String commune, String category,
                    String imageUrl, LocalDate startDate, LocalDate endDate, String sourceUrl, boolean active) {

    public LocalDate lastDay() {
        return endDate != null ? endDate : startDate;
    }

    /** Past its last day — no new bookings or cancellations. */
    public boolean hasEnded(LocalDate today) {
        return lastDay().isBefore(today);
    }
}
