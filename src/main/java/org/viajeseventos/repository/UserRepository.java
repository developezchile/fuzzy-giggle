package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plain JDBC access to {@code users}, joined with its {@code profiles} and {@code companies} rows for display — no ORM, no reflection. */
public final class UserRepository {

    private static final String SELECT_WITH_PROFILE = """
            SELECT u.*, p.code AS profile_code, p.name AS profile_name,
                   c.name AS company_name, c.slug AS company_slug, c.active AS company_active
            FROM users u
            JOIN profiles p ON p.id = u.profile_id
            JOIN companies c ON c.id = u.company_id
            """;

    private final ConnectionPool pool;

    public UserRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public boolean existsByUsername(String username) {
        return exists("SELECT 1 FROM users WHERE username = ?", username);
    }

    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM users WHERE email = ?", email);
    }

    public boolean existsPlatformAdmin() {
        return exists("SELECT 1 FROM users WHERE platform_admin = ? LIMIT 1", true);
    }

    private boolean exists(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence in users", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Used to refuse the change that would leave a company with no enabled administrator. */
    public long countEnabledByCompanyAndProfile(long companyId, long profileId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE company_id = ? AND profile_id = ? AND enabled")) {
            ps.setLong(1, companyId);
            ps.setLong(2, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count users", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<User> findById(long id) {
        return findOne(SELECT_WITH_PROFILE + " WHERE u.id = ?", id);
    }

    public Optional<User> findByUsername(String username) {
        return findOne(SELECT_WITH_PROFILE + " WHERE u.username = ?", username);
    }

    public Optional<User> findByEmail(String email) {
        return findOne(SELECT_WITH_PROFILE + " WHERE u.email = ?", email);
    }

    private Optional<User> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query users", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<User> findAllByCompanyId(long companyId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_WITH_PROFILE + " WHERE u.company_id = ? ORDER BY u.id")) {
            ps.setLong(1, companyId);
            List<User> users = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
            return users;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query users", e);
        } finally {
            pool.release(conn);
        }
    }

    public User insert(User user) {
        String sql = """
                INSERT INTO users
                    (username, email, password, first_name, last_name, phone, profile_id, enabled, email_verified,
                     company_id, platform_admin, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getLastName());
            ps.setString(6, user.getPhone());
            ps.setLong(7, user.getProfileId());
            ps.setBoolean(8, user.isEnabled());
            ps.setBoolean(9, user.isEmailVerified());
            ps.setLong(10, user.getCompanyId());
            ps.setBoolean(11, user.isPlatformAdmin());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert user", e);
        } finally {
            pool.release(conn);
        }
    }

    /** An account never moves between companies and never gains or loses platform_admin, so neither is updated. */
    public User update(User user) {
        String sql = """
                UPDATE users SET
                    username = ?, email = ?, password = ?, first_name = ?, last_name = ?,
                    phone = ?, profile_id = ?, enabled = ?, email_verified = ?, updated_at = NOW()
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getLastName());
            ps.setString(6, user.getPhone());
            ps.setLong(7, user.getProfileId());
            ps.setBoolean(8, user.isEnabled());
            ps.setBoolean(9, user.isEmailVerified());
            ps.setLong(10, user.getId());
            ps.executeUpdate();
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update user", e);
        } finally {
            pool.release(conn);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhone(rs.getString("phone"));
        user.setProfileId(rs.getLong("profile_id"));
        user.setProfileCode(rs.getString("profile_code"));
        user.setProfileName(rs.getString("profile_name"));
        user.setCompanyId(rs.getLong("company_id"));
        user.setCompanyName(rs.getString("company_name"));
        user.setCompanySlug(rs.getString("company_slug"));
        user.setCompanyActive(rs.getBoolean("company_active"));
        user.setPlatformAdmin(rs.getBoolean("platform_admin"));
        user.setEnabled(rs.getBoolean("enabled"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        user.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        user.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return user;
    }
}
