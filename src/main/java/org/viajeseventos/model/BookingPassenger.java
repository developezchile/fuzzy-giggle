package org.viajeseventos.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One traveler within a {@link Booking}. {@code position} keeps the order they were entered in.
 *
 * <p>{@code stopId} is the {@link TripStop} they board at. {@code departurePlace} and
 * {@code departureTime} are copied from that stop when the booking is made and never updated: they
 * are what the passenger was told, so correcting a stop afterwards doesn't rewrite tickets already
 * issued. {@code stopId} is null only for the bookings that predate stops (V8), where the free
 * text the passenger typed is all there ever was.
 *
 * <p>{@code priceClp} is what this seat cost when the booking was made, copied from the stop for
 * the same reason as the place and the time: the company's fares change, and a ticket already
 * issued has a price that happened. Without it a fare edit would silently rewrite what past
 * passengers "paid", since nothing else in a booking records an amount.
 *
 * <p>{@code checkedInAt} is set when the driver marks them boarding (V9) — per passenger, because a
 * bus can leave with three of a booking's four people. It's also what earns the right to review the
 * trip: only somebody who actually travelled can say how it went.
 */
public record BookingPassenger(int position, String fullName, String phone, Long stopId, String departurePlace,
                               LocalTime departureTime, String returnPlace, int priceClp,
                               LocalDateTime checkedInAt, Long checkedInBy) {

    /** A passenger as the booking modal submits them: on board nothing yet. */
    public static BookingPassenger booking(int position, String fullName, String phone, long stopId,
                                           String departurePlace, LocalTime departureTime, String returnPlace,
                                           int priceClp) {
        return new BookingPassenger(position, fullName, phone, stopId, departurePlace, departureTime, returnPlace,
                priceClp, null, null);
    }

    public boolean checkedIn() {
        return checkedInAt != null;
    }
}
