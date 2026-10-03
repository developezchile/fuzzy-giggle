package org.viajeseventos.testsupport;

import org.viajeseventos.config.AppConfig;
import org.viajeseventos.config.Env;
import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.db.MigrationRunner;
import org.viajeseventos.model.Company;
import org.viajeseventos.repository.CompanyRepository;

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

    /**
     * The company this deployment serves — the one V5__companies.sql seeds and V13 renames to
     * Busconciertos. Resolved the same way the app resolves it rather than by a hardcoded slug,
     * which is what broke every IT the first time the company was renamed.
     */
    public static long seededCompanyId(ConnectionPool pool) {
        return seededCompany(pool).id();
    }

    /** Its slug — what a registration link carries, and what the public company page is keyed by. */
    public static String seededCompanySlug(ConnectionPool pool) {
        return seededCompany(pool).slug();
    }

    private static Company seededCompany(ConnectionPool pool) {
        return new CompanyRepository(pool).findTheCompany().orElseThrow();
    }

    /** A fresh, empty company — for tests that check one company can't see another's data. */
    public static long newCompanyId(ConnectionPool pool) {
        String unique = String.valueOf(System.nanoTime());
        return new CompanyRepository(pool).insert("Empresa " + unique, "empresa-" + unique, null);
    }
}
