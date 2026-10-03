package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.Booking;
import org.viajeseventos.model.BookingPassenger;
import org.viajeseventos.model.BookingStatus;
import org.viajeseventos.security.TicketCodes;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Plain JDBC access to {@code bookings} + its {@code booking_passengers} child table. */
public final class BookingRepository {

    /**
     * Outcome of a booking attempt: either the new booking's id, or the seats that were actually
     * left when the transaction looked. The caller decides what to do with a refusal (offer the
     * waitlist), so running out of seats isn't an exception — it's an answer.
     */
    public record InsertResult(Long bookingId, int seatsLeft) {

        public boolean created() {
            return bookingId != null;
        }
    }

    /** How many times a colliding ticket code is retried before giving up — one is already plenty. */
    private static final int TICKET_CODE_ATTEMPTS = 5;

    private static final String SELECT_BOOKINGS = """
            SELECT b.id, b.user_id, b.status, b.created_at, b.cancelled_at, b.ticket_code,
                   u.email AS user_email,
                   TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS user_full_name,
                   u.username AS user_username,
                   t.id AS t_id, t.company_id AS t_company_id, t.origin_commune AS t_origin_commune,
                   t.departure_at AS t_departure_at, t.return_at AS t_return_at,
                   t.seats_total AS t_seats_total, t.min_seats AS t_min_seats,
                   t.booking_deadline AS t_booking_deadline, t.status AS t_status, t.notes AS t_notes,
                   t.route_id AS t_route_id, r.name AS t_route_name,
                   r.bus_type_id AS t_bus_type_id, bt.name AS t_bus_type_name,
                   (SELECT COUNT(*) FROM booking_passengers bp2
                      JOIN bookings b2 ON b2.id = bp2.booking_id
                     WHERE b2.trip_id = t.id AND b2.status = 'CONFIRMED') AS t_seats_taken,
                   e.id AS e_id, e.slug AS e_slug, e.name AS e_name, e.venue AS e_venue, e.commune AS e_commune,
                   e.category AS e_category, e.image_url AS e_image_url, e.start_date AS e_start_date,
                   e.end_date AS e_end_date, e.source_url AS e_source_url, e.active AS e_active,
                   -- V16 agregó events.source y EventRepository.mapRow lo lee: estas consultas
                   -- nombran las columnas una por una, así que hay que listarlo acá también.
                   e.source AS e_source
            FROM bookings b
            JOIN trips t ON t.id = b.trip_id
            JOIN events e ON e.id = t.event_id
            LEFT JOIN routes r ON r.id = t.route_id
            LEFT JOIN bus_types bt ON bt.id = r.bus_type_id
            JOIN users u ON u.id = b.user_id
            """;

    private final ConnectionPool pool;

    public BookingRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Books the passengers on a trip, or refuses because the bus is full — counting the seats and
     * taking them in the same transaction.
     *
     * <p>The {@code SELECT … FOR UPDATE} on the trip row is the whole point: without it, two people
     * booking the last seats at the same moment both count the same free seats and both get in,
     * and the overselling only shows up on the roadside. Locking the trip serialises the count and
     * the insert, so the second one waits and then sees the first one's passengers.
     */
    public InsertResult insert(long tripId, long userId, List<BookingPassenger> passengers) {
        Connection conn = pool.borrow();
        try {
            conn.setAutoCommit(false);
            int seatsTotal;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT seats_total FROM trips WHERE id = ? FOR UPDATE")) {
                ps.setLong(1, tripId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return new InsertResult(null, 0);
                    }
                    seatsTotal = rs.getInt("seats_total");
                }
            }
            int taken = countSeats(conn, tripId);
            int seatsLeft = Math.max(0, seatsTotal - taken);
            if (passengers.size() > seatsLeft) {
                conn.rollback();
                return new InsertResult(null, seatsLeft);
            }

            long bookingId = insertBooking(conn, tripId, userId);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO booking_passengers
                        (booking_id, position, full_name, phone, stop_id, departure_place, departure_time,
                         return_place, price_clp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (BookingPassenger p : passengers) {
                    ps.setLong(1, bookingId);
                    ps.setInt(2, p.position());
                    ps.setString(3, p.fullName());
                    ps.setString(4, p.phone());
                    if (p.stopId() != null) ps.setLong(5, p.stopId());
                    else ps.setNull(5, Types.BIGINT);
                    ps.setString(6, p.departurePlace());
                    ps.setTime(7, Time.valueOf(p.departureTime()));
                    ps.setString(8, p.returnPlace());
                    ps.setInt(9, p.priceClp());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return new InsertResult(bookingId, seatsLeft - passengers.size());
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Failed to insert booking", e);
        } finally {
            resetQuietly(conn);
            pool.release(conn);
        }
    }

    /**
     * Inserts the booking row with a fresh ticket code. The unique index on the code is the real
     * guarantee, so a collision is retried rather than prevented by checking first — checking
     * first would be a race anyway.
     */
    private long insertBooking(Connection conn, long tripId, long userId) throws SQLException {
        SQLException lastFailure = null;
        for (int attempt = 0; attempt < TICKET_CODE_ATTEMPTS; attempt++) {
            Savepoint savepoint = conn.setSavepoint("ticket_code");
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO bookings (trip_id, user_id, status, created_at, ticket_code)
                    VALUES (?, ?, 'CONFIRMED', NOW(), ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, tripId);
                ps.setLong(2, userId);
                ps.setString(3, TicketCodes.generate());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    conn.releaseSavepoint(savepoint);
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                // 23505 = unique violation: the code was taken, so draw another one.
                if (!"23505".equals(e.getSQLState())) throw e;
                conn.rollback(savepoint);
                lastFailure = e;
            }
        }
        throw new SQLException("Could not generate a free ticket code after " + TICKET_CODE_ATTEMPTS
                + " attempts", lastFailure);
    }

    /** Confirmed passengers on a trip — what fills it up. */
    public int countSeats(long tripId) {
        Connection conn = pool.borrow();
        try {
            return countSeats(conn, tripId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count booked seats", e);
        } finally {
            pool.release(conn);
        }
    }

    private int countSeats(Connection conn, long tripId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) AS seats
                FROM booking_passengers bp
                JOIN bookings b ON b.id = bp.booking_id
                WHERE b.trip_id = ? AND b.status = 'CONFIRMED'
                """)) {
            ps.setLong(1, tripId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("seats");
            }
        }
    }

    public Optional<Booking> findById(long id) {
        List<Booking> found = query(SELECT_BOOKINGS + " WHERE b.id = ?", id);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /**
     * The booking a ticket code belongs to, within one trip of one company. Scoped to the trip on
     * purpose: a driver reading a code at their stop must not be able to pull up a booking from
     * somebody else's bus.
     */
    public Optional<Booking> findByTicketCode(long companyId, long tripId, String ticketCode) {
        List<Booking> found = query(SELECT_BOOKINGS
                + " WHERE t.company_id = ? AND b.trip_id = ? AND b.ticket_code = ?", companyId, tripId, ticketCode);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /**
     * Marks one passenger as boarded, or un-marks them. Returns false when the row didn't move,
     * which means the passenger was already in that state — the driver tapped twice.
     */
    public boolean setCheckedIn(long bookingId, int position, boolean checkedIn, long byUserId) {
        String sql = checkedIn
                ? """
                  UPDATE booking_passengers SET checked_in_at = NOW(), checked_in_by = ?
                  WHERE booking_id = ? AND position = ? AND checked_in_at IS NULL
                  """
                : """
                  UPDATE booking_passengers SET checked_in_at = NULL, checked_in_by = NULL
                  WHERE booking_id = ? AND position = ? AND checked_in_at IS NOT NULL
                  """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            if (checkedIn) ps.setLong(index++, byUserId);
            ps.setLong(index++, bookingId);
            ps.setInt(index, position);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update the check-in", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Every confirmed booking of a trip, for the driver's manifest. */
    public List<Booking> findConfirmedByTrip(long tripId) {
        return query(SELECT_BOOKINGS + " WHERE b.trip_id = ? AND b.status = 'CONFIRMED' ORDER BY b.id", tripId);
    }

    /** "Mis reservas": newest first, cancelled ones included so the user sees what happened. */
    public List<Booking> findByUserId(long userId) {
        return query(SELECT_BOOKINGS + " WHERE b.user_id = ? ORDER BY b.created_at DESC, b.id DESC", userId);
    }

    /**
     * Operator view: every booking of the company, narrowed to one trip or to every trip of one
     * event, ordered by departure then creation.
     */
    public List<Booking> findAll(long companyId, Long tripId, Long eventId) {
        if (tripId != null) {
            return query(SELECT_BOOKINGS + " WHERE t.company_id = ? AND b.trip_id = ? ORDER BY b.created_at, b.id",
                    companyId, tripId);
        }
        if (eventId != null) {
            return query(SELECT_BOOKINGS + """
                    WHERE t.company_id = ? AND t.event_id = ?
                    ORDER BY t.departure_at, b.created_at, b.id
                    """, companyId, eventId);
        }
        return query(SELECT_BOOKINGS + " WHERE t.company_id = ? ORDER BY t.departure_at, b.created_at, b.id",
                companyId);
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
                    byBooking.computeIfAbsent(rs.getLong("booking_id"), k -> new ArrayList<>())
                            .add(mapPassenger(rs));
                }
            }
        } finally {
            ids.free();
        }
        return byBooking;
    }

    static BookingPassenger mapPassenger(ResultSet rs) throws SQLException {
        long stopId = rs.getLong("stop_id");
        Long stop = rs.wasNull() ? null : stopId;
        long checkedInBy = rs.getLong("checked_in_by");
        Long by = rs.wasNull() ? null : checkedInBy;
        Timestamp checkedInAt = rs.getTimestamp("checked_in_at");
        return new BookingPassenger(
                rs.getInt("position"),
                rs.getString("full_name"),
                rs.getString("phone"),
                stop,
                rs.getString("departure_place"),
                rs.getTime("departure_time").toLocalTime(),
                rs.getString("return_place"),
                rs.getInt("price_clp"),
                checkedInAt != null ? checkedInAt.toLocalDateTime() : null,
                by);
    }

    private Booking mapRow(ResultSet rs) throws SQLException {
        String fullName = rs.getString("user_full_name");
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        return new Booking(
                rs.getLong("id"),
                rs.getString("ticket_code"),
                TripRepository.mapRow(rs, "t_"),
                rs.getLong("user_id"),
                rs.getString("user_email"),
                fullName == null || fullName.isBlank() ? rs.getString("user_username") : fullName,
                BookingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                cancelledAt != null ? cancelledAt.toLocalDateTime() : null,
                List.of());
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private void resetQuietly(Connection conn) {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best effort
        }
    }
}
