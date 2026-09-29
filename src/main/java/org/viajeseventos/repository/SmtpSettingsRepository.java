package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.SmtpSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/** Single-row table (id is always 1) — see {@code V3__smtp_settings.sql}. */
public final class SmtpSettingsRepository {

    private final ConnectionPool pool;

    public SmtpSettingsRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<SmtpSettings> find() {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM smtp_settings WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query smtp_settings", e);
        } finally {
            pool.release(conn);
        }
    }

    public SmtpSettings save(SmtpSettings settings) {
        String sql = """
                INSERT INTO smtp_settings (id, provider, host, port, username, password, start_tls,
                                            from_address, from_name, enabled, updated_at)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    provider = EXCLUDED.provider, host = EXCLUDED.host, port = EXCLUDED.port,
                    username = EXCLUDED.username, password = EXCLUDED.password, start_tls = EXCLUDED.start_tls,
                    from_address = EXCLUDED.from_address, from_name = EXCLUDED.from_name,
                    enabled = EXCLUDED.enabled, updated_at = NOW()
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, settings.getProvider());
            ps.setString(2, settings.getHost());
            ps.setObject(3, settings.getPort());
            ps.setString(4, settings.getUsername());
            ps.setString(5, settings.getPassword());
            ps.setBoolean(6, settings.isStartTls());
            ps.setString(7, settings.getFromAddress());
            ps.setString(8, settings.getFromName());
            ps.setBoolean(9, settings.isEnabled());
            ps.executeUpdate();
            return find().orElseThrow();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save smtp_settings", e);
        } finally {
            pool.release(conn);
        }
    }

    private SmtpSettings mapRow(ResultSet rs) throws SQLException {
        SmtpSettings s = new SmtpSettings();
        s.setProvider(rs.getString("provider"));
        s.setHost(rs.getString("host"));
        Object port = rs.getObject("port");
        s.setPort(port != null ? ((Number) port).intValue() : null);
        s.setUsername(rs.getString("username"));
        s.setPassword(rs.getString("password"));
        s.setStartTls(rs.getBoolean("start_tls"));
        s.setFromAddress(rs.getString("from_address"));
        s.setFromName(rs.getString("from_name"));
        s.setEnabled(rs.getBoolean("enabled"));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        s.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return s;
    }
}
