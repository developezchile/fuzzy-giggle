package org.viajeseventos.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A group of passengers booked on one {@link Trip} by one account (one "Viajar" checkout). Loaded
 * with its trip — and through it the event — plus the booking account's contact data, which is
 * what both "Mis reservas" and the operator's BOOKINGS view display.
 *
 * <p>{@code ticketCode} is what the passenger shows and the driver reads at the stop: it belongs to
 * the booking, not to each passenger, because a group books together and arrives together.
 */
public record Booking(long id, String ticketCode, Trip trip, long userId, String userEmail, String userName,
                      BookingStatus status, LocalDateTime createdAt, LocalDateTime cancelledAt,
                      List<BookingPassenger> passengers) {

    public Booking withPassengers(List<BookingPassenger> passengers) {
        return new Booking(id, ticketCode, trip, userId, userEmail, userName, status, createdAt, cancelledAt,
                passengers);
    }

    public Event event() {
        return trip.event();
    }

    /** Seats this booking holds — one per passenger. Zero once cancelled. */
    public int seats() {
        return status == BookingStatus.CANCELLED ? 0 : passengers.size();
    }

    /** Whether anybody on this booking actually boarded — what unlocks reviewing the trip. */
    public boolean anyCheckedIn() {
        return passengers.stream().anyMatch(BookingPassenger::checkedIn);
    }
}
