package org.viajeseventos.testsupport;

import org.viajeseventos.config.AppConfig;
import org.viajeseventos.config.Env;
import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.db.MigrationRunner;

/**
 * Integration tests (suffix {@code IT}) run against a real local Postgres — no mocking the
 * database. They use their own database ({@code TEST_DB_URL}, default {@code viajes_eventos_test})
 * rather than the dev one, since some tests rearrange data (e.g. leave a single enabled admin).
 * Credentials come from the same {@code DB_USERNAME}/{@code DB_PASSWORD} as the app.
 * Create it once with {@code createdb viajes_eventos_test}.
 */
public final class TestDb {

    private TestDb() {
    }

    public static ConnectionPool pool() {
        AppConfig config = new AppConfig();
        String url = Env.get("TEST_DB_URL", "jdbc:postgresql://localhost:5432/viajes_eventos_test");
        ConnectionPool pool = new ConnectionPool(url, config.dbUsername, config.dbPassword, 5);
        new MigrationRunner(pool).migrate();
        return pool;
    }
}
