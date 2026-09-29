package org.viajeseventos.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A group of passengers booked for one event by one account (one "Viajar" checkout). Loaded with
 * its event and the booking account's contact data, which is what both "Mis reservas" and the
 * admin's BOOKINGS view display.
 */
public record Booking(long id, Event event, long userId, String userEmail, String userName, BookingStatus status,
                      LocalDateTime createdAt, LocalDateTime cancelledAt, List<BookingPassenger> passengers) {

    public Booking withPassengers(List<BookingPassenger> passengers) {
        return new Booking(id, event, userId, userEmail, userName, status, createdAt, cancelledAt, passengers);
    }
}
