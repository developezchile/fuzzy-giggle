package org.viajeseventos.model;

import java.time.LocalTime;

/** One traveler within a {@link Booking}. {@code position} keeps the order they were entered in. */
public record BookingPassenger(int position, String fullName, String phone, String departurePlace,
                               LocalTime departureTime, String returnPlace) {
}
