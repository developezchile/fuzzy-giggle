package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.Company;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plain JDBC access to {@code companies}. */
public final class CompanyRepository {

    /** A company plus its size — for the platform's COMPANIES list. */
    public record Listing(Company company, int userCount, int eventCount) {
    }

    private final ConnectionPool pool;

    public CompanyRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<Company> findById(long id) {
        return findOne("SELECT * FROM companies WHERE id = ?", id);
    }

    public Optional<Company> findBySlug(String slug) {
        return findOne("SELECT * FROM companies WHERE slug = ?", slug);
    }

    public boolean existsBySlug(String slug) {
        return findBySlug(slug).isPresent();
    }

    private Optional<Company> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query companies", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Newest first. */
    public List<Listing> findAll() {
        String sql = """
                SELECT c.*,
                       (SELECT COUNT(*) FROM users u WHERE u.company_id = c.id) AS user_count,
                       (SELECT COUNT(*) FROM events e WHERE e.company_id = c.id) AS event_count
                FROM companies c
                ORDER BY c.created_at DESC, c.id DESC
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Listing> listings = new ArrayList<>();
            while (rs.next()) {
                listings.add(new Listing(mapRow(rs), rs.getInt("user_count"), rs.getInt("event_count")));
            }
            return listings;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query companies", e);
        } finally {
            pool.release(conn);
        }
    }

    public long insert(String name, String slug, String contactEmail) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO companies (name, slug, contact_email, active, created_at, updated_at) VALUES (?, ?, ?, TRUE, NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setString(3, contactEmail);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert company", e);
        } finally {
            pool.release(conn);
        }
    }

    /** The slug is fixed at creation (clients' registration links use it), so it isn't updated. */
    public void update(long id, String name, String contactEmail) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE companies SET name = ?, contact_email = ?, updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, contactEmail);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update company", e);
        } finally {
            pool.release(conn);
        }
    }

    public void setActive(long id, boolean active) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE companies SET active = ?, updated_at = NOW() WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update company", e);
        } finally {
            pool.release(conn);
        }
    }

    private static Company mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new Company(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("contact_email"),
                rs.getBoolean("active"),
                createdAt != null ? createdAt.toLocalDateTime() : null);
    }
}
