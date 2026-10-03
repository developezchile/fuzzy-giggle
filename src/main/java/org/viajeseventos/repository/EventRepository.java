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

    /**
     * Any event plus the totals of everything published over it — for the EVENT_ADMIN list. Since
     * V8 the bookings hang off the event's trips, not off the event itself.
     */
    /**
     * An event plus what hangs off it. {@code bookingCount} and {@code confirmedPassengers} both
     * count only confirmed bookings, so the two always agree with each other.
     *
     * <p>{@code tripsWithoutRoute} is the count of its departures
     * that don't run a route yet: those have no stops, so nobody can say where they board, and no
     * price, so they read as free. It's the one thing about an event that silently breaks booking,
     * which is why the listing carries it instead of making the operator open each departure.
     */
    public record AdminListing(Event event, int tripCount, int tripsWithoutRoute, int bookingCount,
                               int confirmedPassengers) {
    }

    private final ConnectionPool pool;

    public EventRepository(ConnectionPool pool) {
        this.pool = pool;
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
                       (SELECT COUNT(*) FROM trips t WHERE t.event_id = e.id) AS trip_count,
                       (SELECT COUNT(*) FROM trips t WHERE t.event_id = e.id AND t.route_id IS NULL)
                           AS trips_without_route,
                       -- Solo confirmadas, igual que confirmed_passengers: contar también las
                       -- canceladas dejaba la columna diciéndose sola que no ("1 pasajero ·
                       -- 2 reservas"). Las canceladas se ven en Reservas, que tiene su filtro.
                       (SELECT COUNT(*) FROM bookings b JOIN trips t ON t.id = b.trip_id
                         WHERE t.event_id = e.id AND b.status = 'CONFIRMED') AS booking_count,
                       (SELECT COUNT(*) FROM booking_passengers bp
                          JOIN bookings b ON b.id = bp.booking_id
                          JOIN trips t ON t.id = b.trip_id
                         WHERE t.event_id = e.id AND b.status = 'CONFIRMED') AS confirmed_passengers
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
                    listings.add(new AdminListing(mapRow(rs), rs.getInt("trip_count"),
                            rs.getInt("trips_without_route"), rs.getInt("booking_count"),
                            rs.getInt("confirmed_passengers")));
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

    /**
     * Whether anything was ever published over this event. A trip is enough to keep it: deleting
     * the event would take the departure — and any booking on it — with it.
     */
    public boolean hasTrips(long eventId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM trips WHERE event_id = ? LIMIT 1")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query trips", e);
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
    /** Solo activa o desactiva, sin tocar el resto: lo contrario reescribiría campos que nadie pidió cambiar. */
    public void setActive(long companyId, long id, boolean active) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE events SET active = ? WHERE id = ? AND company_id = ?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.setLong(3, companyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update event state", e);
        } finally {
            pool.release(conn);
        }
    }

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

    /** By the slug in its public URL — the lookup behind {@code /evento/<slug>}. */
    public Optional<Event> findBySlug(long companyId, String slug) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM events WHERE company_id = ? AND slug = ?")) {
            ps.setLong(1, companyId);
            ps.setString(2, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events", e);
        } finally {
            pool.release(conn);
        }
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
                rs.getBoolean(prefix + "active"),
                rs.getString(prefix + "source"));
    }

    private static Event mapRow(ResultSet rs) throws SQLException {
        return mapRow(rs, "");
    }
}
