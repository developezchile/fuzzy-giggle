package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Plain JDBC access to {@code profiles} + its {@code profile_modules} child table. */
public final class ProfileRepository {

    private static final String SELECT_WITH_USER_COUNT = """
            SELECT p.*, (SELECT COUNT(*) FROM users u WHERE u.profile_id = p.id) AS user_count
            FROM profiles p
            """;

    private final ConnectionPool pool;

    public ProfileRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    /** System profiles first (ADMIN, CLIENT), then the rest alphabetically. */
    public List<Profile> findAll() {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                SELECT_WITH_USER_COUNT
                        + " ORDER BY p.code IS NULL, CASE WHEN p.code IS NOT NULL THEN p.id END, LOWER(p.name)");
             ResultSet rs = ps.executeQuery()) {
            List<Profile> profiles = new ArrayList<>();
            while (rs.next()) {
                Profile profile = mapRow(rs);
                profile.setModules(loadModules(conn, profile.getId()));
                profiles.add(profile);
            }
            return profiles;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query profiles", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<Profile> findById(long id) {
        return findOne(SELECT_WITH_USER_COUNT + " WHERE p.id = ?", id);
    }

    public Optional<Profile> findByCode(String code) {
        return findOne(SELECT_WITH_USER_COUNT + " WHERE p.code = ?", code);
    }

    private Optional<Profile> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Profile profile = mapRow(rs);
                profile.setModules(loadModules(conn, profile.getId()));
                return Optional.of(profile);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query profiles", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Case-insensitive, matching the {@code uk_profiles_name_ci} index. {@code excludeId} skips the profile being edited. */
    public boolean existsByName(String name, Long excludeId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM profiles WHERE LOWER(name) = LOWER(?) AND id <> ?")) {
            ps.setString(1, name);
            ps.setLong(2, excludeId != null ? excludeId : -1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check profile name", e);
        } finally {
            pool.release(conn);
        }
    }

    /** What an enabled account may do, and which company it acts for. */
    public record Access(long companyId, Set<AppModule> modules) {
    }

    /**
     * The modules an <em>enabled</em> user has — empty for a disabled or unknown user. Read fresh on
     * every module-gated request (see {@code ModuleAccess}), so a profile edit, a profile
     * reassignment, disabling an account or its company takes effect immediately, not at token expiry.
     * The profile's modules count only while the company is active; platform modules come from the
     * account's {@code platform_admin} flag, never from the profile.
     */
    public Set<AppModule> findModulesByEnabledUserId(long userId) {
        return findAccessByEnabledUserId(userId).map(Access::modules).orElse(EnumSet.noneOf(AppModule.class));
    }

    public Optional<Access> findAccessByEnabledUserId(long userId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT u.company_id, u.platform_admin, c.active AS company_active, pm.module_key
                FROM users u
                JOIN companies c ON c.id = u.company_id
                LEFT JOIN profile_modules pm ON pm.profile_id = u.profile_id
                WHERE u.id = ? AND u.enabled
                """)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                Long companyId = null;
                Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
                while (rs.next()) {
                    companyId = rs.getLong("company_id");
                    if (rs.getBoolean("platform_admin")) {
                        modules.addAll(AppModule.platformModules());
                    }
                    String key = rs.getString("module_key");
                    if (key != null && rs.getBoolean("company_active")) {
                        AppModule.fromKey(key).filter(m -> !m.platform()).ifPresent(modules::add);
                    }
                }
                return companyId == null ? Optional.empty() : Optional.of(new Access(companyId, modules));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query user modules", e);
        } finally {
            pool.release(conn);
        }
    }

    public Profile insert(Profile profile) {
        return inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO profiles (code, name, description, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, profile.getCode());
                ps.setString(2, profile.getName());
                ps.setString(3, profile.getDescription());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) profile.setId(keys.getLong(1));
                }
            }
            saveModules(conn, profile.getId(), profile.getModules());
            return profile;
        });
    }

    public Profile update(Profile profile) {
        return inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE profiles SET name = ?, description = ?, updated_at = NOW() WHERE id = ?")) {
                ps.setString(1, profile.getName());
                ps.setString(2, profile.getDescription());
                ps.setLong(3, profile.getId());
                ps.executeUpdate();
            }
            saveModules(conn, profile.getId(), profile.getModules());
            return profile;
        });
    }

    /** {@code profile_modules} rows go with it (ON DELETE CASCADE); assigned users block it (FK). */
    public void delete(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM profiles WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete profile", e);
        } finally {
            pool.release(conn);
        }
    }

    private Set<AppModule> loadModules(Connection conn, long profileId) throws SQLException {
        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        try (PreparedStatement ps = conn.prepareStatement("SELECT module_key FROM profile_modules WHERE profile_id = ?")) {
            ps.setLong(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AppModule.fromKey(rs.getString("module_key")).ifPresent(modules::add);
                }
            }
        }
        return modules;
    }

    private void saveModules(Connection conn, long profileId, Set<AppModule> modules) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM profile_modules WHERE profile_id = ?")) {
            delete.setLong(1, profileId);
            delete.executeUpdate();
        }
        if (modules == null || modules.isEmpty()) return;
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO profile_modules (profile_id, module_key) VALUES (?, ?)")) {
            for (AppModule module : modules) {
                insert.setLong(1, profileId);
                insert.setString(2, module.name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Profile inTransaction(SqlWork<Profile> work) {
        Connection conn = pool.borrow();
        try {
            conn.setAutoCommit(false);
            Profile result = work.run(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // best effort
            }
            throw new RuntimeException("Failed to save profile", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
            pool.release(conn);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection conn) throws SQLException;
    }

    private Profile mapRow(ResultSet rs) throws SQLException {
        Profile profile = new Profile();
        profile.setId(rs.getLong("id"));
        profile.setCode(rs.getString("code"));
        profile.setName(rs.getString("name"));
        profile.setDescription(rs.getString("description"));
        profile.setUserCount(rs.getLong("user_count"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        profile.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        profile.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return profile;
    }
}
