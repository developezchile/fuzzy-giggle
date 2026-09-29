package org.viajeseventos.db;

import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Small blocking-queue connection pool over {@link DriverManager}. Deliberately hand-rolled
 * instead of pulling in HikariCP/Agroal: it keeps the dependency surface at just the JDBC driver.
 */
public final class ConnectionPool implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(ConnectionPool.class);

    private final String url;
    private final Properties props;
    private final int maxSize;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private int created = 0;
    private volatile boolean closed = false;

    public ConnectionPool(String url, String username, String password, int maxSize) {
        this.url = url;
        this.maxSize = maxSize;
        this.props = new Properties();
        this.props.setProperty("user", username);
        this.props.setProperty("password", password);
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver not found on classpath", e);
        }
        log.info("Database connection pool started: url={} maxSize={}", url, maxSize);
    }

    public Connection borrow() {
        lock.lock();
        try {
            while (true) {
                if (closed) throw new IllegalStateException("Connection pool is closed");
                Connection candidate = idle.poll();
                if (candidate != null) {
                    if (isValid(candidate)) {
                        return candidate;
                    }
                    log.debug("Discarding invalid pooled connection ({}/{} in use)", created - 1, maxSize);
                    closeQuietly(candidate);
                    created--;
                    continue;
                }
                if (created < maxSize) {
                    created++;
                    break;
                }
                log.debug("Connection pool exhausted ({}/{} in use), waiting for one to free up", created, maxSize);
                try {
                    if (!available.await(10, TimeUnit.SECONDS)) {
                        log.warn("Timed out after 10s waiting for a database connection ({}/{} in use)", created, maxSize);
                        throw new RuntimeException("Timed out waiting for a database connection");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for a database connection", e);
                }
            }
        } finally {
            lock.unlock();
        }
        // Opening the socket happens outside the lock so it doesn't block other borrowers.
        try {
            Connection connection = DriverManager.getConnection(url, props);
            log.debug("Opened new database connection ({}/{} in use)", created, maxSize);
            return connection;
        } catch (SQLException e) {
            lock.lock();
            try {
                created--;
                available.signal();
            } finally {
                lock.unlock();
            }
            log.error("Failed to open database connection", e);
            throw new RuntimeException("Failed to open database connection", e);
        }
    }

    public void release(Connection connection) {
        if (connection == null) return;
        lock.lock();
        try {
            if (closed) {
                closeQuietly(connection);
                return;
            }
            idle.push(connection);
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    private boolean isValid(Connection c) {
        try {
            return c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            log.info("Closing database connection pool ({} idle connections)", idle.size());
            Connection c;
            while ((c = idle.poll()) != null) {
                closeQuietly(c);
            }
        } finally {
            lock.unlock();
        }
    }
}
