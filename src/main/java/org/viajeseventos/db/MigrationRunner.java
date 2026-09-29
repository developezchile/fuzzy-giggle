package org.viajeseventos.db;

import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Minimal versioned-SQL migration runner — no Flyway. Applies any file in {@code migrations/}
 * (below) not yet recorded in {@code schema_migrations}, in order, each in its own transaction.
 * The Postgres JDBC driver's plain {@link Statement} (not {@link PreparedStatement}) executes a
 * whole semicolon-delimited script in one call via the simple query protocol, so each migration
 * file can contain multiple statements.
 *
 * <p>Classpath resources can't be listed reliably from inside a shaded jar, so migration files
 * are named explicitly here rather than discovered by scanning a directory. Add new files to
 * {@code src/main/resources/db/migrations/} <em>and</em> to {@link #MIGRATIONS} below, in order.
 */
public final class MigrationRunner {

    private static final Logger log = LogManager.getLogger(MigrationRunner.class);

    /** Ordered filename -> description. Version is parsed from the filename (e.g. "V1" from "V1__init.sql"). */
    private static final List<String> MIGRATIONS = List.of(
            "V1__init.sql",
            "V2__auth_tokens.sql",
            "V3__events_bookings.sql",
            "V4__smtp_settings.sql"
    );

    private final ConnectionPool pool;

    public MigrationRunner(ConnectionPool pool) {
        this.pool = pool;
    }

    public void migrate() {
        Connection conn = pool.borrow();
        try {
            ensureHistoryTable(conn);
            for (String file : MIGRATIONS) {
                String version = version(file);
                if (isApplied(conn, version)) continue;
                apply(conn, version, file);
            }
        } finally {
            pool.release(conn);
        }
    }

    private void ensureHistoryTable(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version    VARCHAR(50) PRIMARY KEY,
                        filename   VARCHAR(255) NOT NULL,
                        applied_at TIMESTAMP NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create schema_migrations table", e);
        }
    }

    private boolean isApplied(Connection conn, String version) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check schema_migrations", e);
        }
    }

    private void apply(Connection conn, String version, String file) {
        String sql = readResource("/db/migrations/" + file);
        try {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_migrations (version, filename, applied_at) VALUES (?, ?, ?)")) {
                ps.setString(1, version);
                ps.setString(2, file);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }
            conn.commit();
            log.info("Applied migration {} ({})", version, file);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new IllegalStateException("Failed to apply migration " + file, e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private String version(String filename) {
        int idx = filename.indexOf("__");
        if (idx < 0) throw new IllegalArgumentException("Migration filename must be V<n>__description.sql: " + filename);
        return filename.substring(0, idx);
    }

    private String readResource(String path) {
        try (InputStream in = MigrationRunner.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Migration resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration resource: " + path, e);
        }
    }
}
