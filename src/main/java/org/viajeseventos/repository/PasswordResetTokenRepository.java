package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.PasswordResetToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public final class PasswordResetTokenRepository {

    private final ConnectionPool pool;

    public PasswordResetTokenRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public PasswordResetToken insert(PasswordResetToken token) {
        String sql = """
                INSERT INTO password_reset_tokens (user_id, token, expires_at, created_at)
                VALUES (?, ?, ?, NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, token.getUserId());
            ps.setString(2, token.getToken());
            ps.setTimestamp(3, Timestamp.valueOf(token.getExpiresAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) token.setId(keys.getLong(1));
            }
            return token;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert password_reset_token", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM password_reset_tokens WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query password_reset_tokens", e);
        } finally {
            pool.release(conn);
        }
    }

    public void markUsed(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("UPDATE password_reset_tokens SET used_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark password_reset_token used " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    /** Invalidates any outstanding tokens for this user — called before issuing a new one and
     *  after a successful reset, so old links stop working. */
    public void deleteByUserId(long userId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM password_reset_tokens WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete password_reset_tokens for user " + userId, e);
        } finally {
            pool.release(conn);
        }
    }

    private PasswordResetToken mapRow(ResultSet rs) throws SQLException {
        PasswordResetToken t = new PasswordResetToken();
        t.setId(rs.getLong("id"));
        t.setUserId(rs.getLong("user_id"));
        t.setToken(rs.getString("token"));
        t.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        Timestamp usedAt = rs.getTimestamp("used_at");
        t.setUsedAt(usedAt != null ? usedAt.toLocalDateTime() : null);
        Timestamp createdAt = rs.getTimestamp("created_at");
        t.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return t;
    }
}
