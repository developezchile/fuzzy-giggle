package org.viajeseventos.repository;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.model.EmailVerificationToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

public final class EmailVerificationTokenRepository {

    private final ConnectionPool pool;

    public EmailVerificationTokenRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public EmailVerificationToken insert(EmailVerificationToken token) {
        String sql = """
                INSERT INTO email_verification_tokens (user_id, token, expires_at, created_at)
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
            throw new RuntimeException("Failed to insert email_verification_token", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<EmailVerificationToken> findByToken(String token) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM email_verification_tokens WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query email_verification_tokens", e);
        } finally {
            pool.release(conn);
        }
    }

    /** Invalidates any outstanding tokens for this user — called before issuing a new one and
     *  after a successful verification, so old links stop working. */
    public void deleteByUserId(long userId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM email_verification_tokens WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete email_verification_tokens for user " + userId, e);
        } finally {
            pool.release(conn);
        }
    }

    private EmailVerificationToken mapRow(ResultSet rs) throws SQLException {
        EmailVerificationToken t = new EmailVerificationToken();
        t.setId(rs.getLong("id"));
        t.setUserId(rs.getLong("user_id"));
        t.setToken(rs.getString("token"));
        t.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        Timestamp createdAt = rs.getTimestamp("created_at");
        t.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return t;
    }
}
