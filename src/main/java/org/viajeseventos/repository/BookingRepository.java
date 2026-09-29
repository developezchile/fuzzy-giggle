package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingPassenger;
import org.viajeseventos.model.BookingStatus;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Plain JDBC access to {@code bookings} + its {@code booking_passengers} child table. */
public final class BookingRepository {

    private static final String SELECT_BOOKINGS = """
            SELECT b.id, b.user_id, b.status, b.created_at, b.cancelled_at,
                   u.email AS user_email,
                   TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS user_full_name,
                   u.username AS user_username,
                   e.id AS e_id, e.slug AS e_slug, e.name AS e_name, e.venue AS e_venue, e.commune AS e_commune,
                   e.category AS e_category, e.image_url AS e_image_url, e.start_date AS e_start_date,
                   e.end_date AS e_end_date, e.source_url AS e_source_url, e.active AS e_active
            FROM bookings b
            JOIN events e ON e.id = b.event_id
            JOIN users u ON u.id = b.user_id
            """;

    private final ConnectionPool pool;

    public BookingRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    /** Inserts the booking and all its passengers atomically; returns the new booking id. */
    public long insert(long eventId, long userId, List<BookingPassenger> passengers) {
        Connection conn = pool.borrow();
        try {
            conn.setAutoCommit(false);
            long bookingId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bookings (event_id, user_id, status, created_at) VALUES (?, ?, 'CONFIRMED', NOW())",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, eventId);
                ps.setLong(2, userId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    bookingId = keys.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO booking_passengers
                        (booking_id, position, full_name, phone, departure_place, departure_time, return_place)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (BookingPassenger p : passengers) {
                    ps.setLong(1, bookingId);
                    ps.setInt(2, p.position());
                    ps.setString(3, p.fullName());
                    ps.setString(4, p.phone());
                    ps.setString(5, p.departurePlace());
                    ps.setTime(6, Time.valueOf(p.departureTime()));
                    ps.setString(7, p.returnPlace());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return bookingId;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // best effort
            }
            throw new RuntimeException("Failed to insert booking", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
            pool.release(conn);
        }
    }

    public Optional<Booking> findById(long id) {
        List<Booking> found = query(SELECT_BOOKINGS + " WHERE b.id = ?", id);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /** "Mis reservas": newest first, cancelled ones included so the user sees what happened. */
    public List<Booking> findByUserId(long userId) {
        return query(SELECT_BOOKINGS + " WHERE b.user_id = ? ORDER BY b.created_at DESC, b.id DESC", userId);
    }

    /** Admin view: every booking, optionally for one event, ordered by event date then creation. */
    public List<Booking> findAll(Long eventId) {
        return eventId == null
                ? query(SELECT_BOOKINGS + " ORDER BY e.start_date, e.name, b.created_at, b.id")
                : query(SELECT_BOOKINGS + " WHERE b.event_id = ? ORDER BY b.created_at, b.id", eventId);
    }

    public void cancel(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bookings SET status = 'CANCELLED', cancelled_at = NOW() WHERE id = ? AND status = 'CONFIRMED'")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cancel booking", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<Booking> query(String sql, Object... params) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            Map<Long, Booking> bookings = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Booking booking = mapRow(rs);
                    bookings.put(booking.id(), booking);
                }
            }
            if (bookings.isEmpty()) return List.of();

            Map<Long, List<BookingPassenger>> passengers = loadPassengers(conn, bookings.keySet().toArray(Long[]::new));
            List<Booking> result = new ArrayList<>(bookings.size());
            for (Booking booking : bookings.values()) {
                result.add(booking.withPassengers(passengers.getOrDefault(booking.id(), List.of())));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bookings", e);
        } finally {
            pool.release(conn);
        }
    }

    /** One query for every booking's passengers, rather than one per booking. */
    private Map<Long, List<BookingPassenger>> loadPassengers(Connection conn, Long[] bookingIds) throws SQLException {
        Map<Long, List<BookingPassenger>> byBooking = new LinkedHashMap<>();
        Array ids = conn.createArrayOf("bigint", bookingIds);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM booking_passengers WHERE booking_id = ANY (?) ORDER BY booking_id, position")) {
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byBooking.computeIfAbsent(rs.getLong("booking_id"), k -> new ArrayList<>()).add(new BookingPassenger(
                            rs.getInt("position"),
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            rs.getString("departure_place"),
                            rs.getTime("departure_time").toLocalTime(),
                            rs.getString("return_place")));
                }
            }
        } finally {
            ids.free();
        }
        return byBooking;
    }

    private Booking mapRow(ResultSet rs) throws SQLException {
        String fullName = rs.getString("user_full_name");
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        return new Booking(
                rs.getLong("id"),
                EventRepository.mapRow(rs, "e_"),
                rs.getLong("user_id"),
                rs.getString("user_email"),
                fullName == null || fullName.isBlank() ? rs.getString("user_username") : fullName,
                BookingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                cancelledAt != null ? cancelledAt.toLocalDateTime() : null,
                List.of());
    }
}
