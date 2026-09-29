package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.Event;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plain JDBC access to {@code events}. Every query takes the company it's scoped to. */
public final class EventRepository {

    /** Any event plus its booking totals — for the EVENT_ADMIN list. */
    public record AdminListing(Event event, int bookingCount, int confirmedPassengers) {
    }

    /** An active event plus how many passengers the calling account has confirmed on it. */
    public record Listing(Event event, int myPassengerCount) {
    }

    private final ConnectionPool pool;

    public EventRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    /** The company's active events that haven't ended, soonest first. */
    public List<Listing> findUpcoming(long companyId, long userId, LocalDate today) {
        String sql = """
                SELECT e.*,
                       (SELECT COUNT(*) FROM booking_passengers bp
                          JOIN bookings b ON b.id = bp.booking_id
                         WHERE b.event_id = e.id AND b.user_id = ? AND b.status = 'CONFIRMED') AS my_passengers
                FROM events e
                WHERE e.company_id = ? AND e.active AND COALESCE(e.end_date, e.start_date) >= ?
                ORDER BY e.start_date, e.name
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, companyId);
            ps.setDate(3, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                List<Listing> listings = new ArrayList<>();
                while (rs.next()) {
                    listings.add(new Listing(mapRow(rs), rs.getInt("my_passengers")));
                }
                return listings;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Every event of the company, past included — for the admin's filter. */
    public List<Event> findAll(long companyId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM events WHERE company_id = ? ORDER BY start_date, name")) {
            ps.setLong(1, companyId);
            List<Event> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
            return events;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Every event (inactive and past included) with its bookings, newest dates first. */
    public List<AdminListing> findAllForAdmin(long companyId) {
        String sql = """
                SELECT e.*,
                       (SELECT COUNT(*) FROM bookings b WHERE b.event_id = e.id) AS booking_count,
                       (SELECT COUNT(*) FROM booking_passengers bp JOIN bookings b ON b.id = bp.booking_id
                         WHERE b.event_id = e.id AND b.status = 'CONFIRMED') AS confirmed_passengers
                FROM events e
                WHERE e.company_id = ?
                ORDER BY e.start_date DESC, e.name
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, companyId);
            List<AdminListing> listings = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    listings.add(new AdminListing(mapRow(rs), rs.getInt("booking_count"), rs.getInt("confirmed_passengers")));
                }
            }
            return listings;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
    }

    public boolean existsBySlug(long companyId, String slug) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM events WHERE company_id = ? AND slug = ?")) {
            ps.setLong(1, companyId);
            ps.setString(2, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Any booking at all, cancelled included — they're the event's history. */
    public boolean hasBookings(long eventId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM bookings WHERE event_id = ? LIMIT 1")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bookings", e);
        } finally {
            pool.release(conn);
        }
    }

    public long insert(long companyId, Event event) {
        String sql = """
                INSERT INTO events (slug, name, venue, commune, category, image_url, start_date, end_date, source_url, active,
                                    company_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, event);
            ps.setLong(11, companyId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert event", e);
        } finally {
            pool.release(conn);
        }
    }

    /** The slug is fixed at creation (it's the event's stable identifier), so it isn't updated. */
    public void update(long companyId, Event event) {
        String sql = """
                UPDATE events SET name = ?, venue = ?, commune = ?, category = ?, image_url = ?,
                                  start_date = ?, end_date = ?, source_url = ?, active = ?
                WHERE id = ? AND company_id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.name());
            ps.setString(2, event.venue());
            ps.setString(3, event.commune());
            ps.setString(4, event.category());
            ps.setString(5, event.imageUrl());
            ps.setDate(6, Date.valueOf(event.startDate()));
            ps.setDate(7, event.endDate() != null ? Date.valueOf(event.endDate()) : null);
            ps.setString(8, event.sourceUrl());
            ps.setBoolean(9, event.active());
            ps.setLong(10, event.id());
            ps.setLong(11, companyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update event", e);
        } finally {
            pool.release(conn);
        }
    }

    public void delete(long companyId, long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM events WHERE id = ? AND company_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, companyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete event", e);
        } finally {
            pool.release(conn);
        }
    }

    private static void bind(PreparedStatement ps, Event event) throws SQLException {
        ps.setString(1, event.slug());
        ps.setString(2, event.name());
        ps.setString(3, event.venue());
        ps.setString(4, event.commune());
        ps.setString(5, event.category());
        ps.setString(6, event.imageUrl());
        ps.setDate(7, Date.valueOf(event.startDate()));
        ps.setDate(8, event.endDate() != null ? Date.valueOf(event.endDate()) : null);
        ps.setString(9, event.sourceUrl());
        ps.setBoolean(10, event.active());
    }

    /** Empty for another company's event, same as for a missing one. */
    public Optional<Event> findById(long companyId, long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM events WHERE id = ? AND company_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Maps the {@code events} columns; also used by {@link BookingRepository}, whose queries select them with an {@code e_} prefix. */
    static Event mapRow(ResultSet rs, String prefix) throws SQLException {
        Date endDate = rs.getDate(prefix + "end_date");
        return new Event(
                rs.getLong(prefix + "id"),
                rs.getString(prefix + "slug"),
                rs.getString(prefix + "name"),
                rs.getString(prefix + "venue"),
                rs.getString(prefix + "commune"),
                rs.getString(prefix + "category"),
                rs.getString(prefix + "image_url"),
                rs.getDate(prefix + "start_date").toLocalDate(),
                endDate != null ? endDate.toLocalDate() : null,
                rs.getString(prefix + "source_url"),
                rs.getBoolean(prefix + "active"));
    }

    private static Event mapRow(ResultSet rs) throws SQLException {
        return mapRow(rs, "");
    }
}
