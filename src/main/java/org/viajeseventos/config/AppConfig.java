package org.viajeseventos.config;

import java.net.URI;

/**
 * Central place where every environment-derived setting is read once at startup, via
 * {@link Env} (env var &gt; {@code config.yml} &gt; the hardcoded default passed below).
 */
public final class AppConfig {

    public final int port;
    public final String contextPath;

    public final String dbUrl;
    public final String dbUsername;
    public final String dbPassword;
    public final int dbPoolSize;

    public final String jwtSecret;
    public final long jwtExpirationMs;

    /**
     * The company this deployment belongs to, by slug. One deployment serves one company, so the
     * public pages resolve it from here instead of from the URL — nobody searches a slug. Blank
     * (the default) means "the only company there is", which is right for a fresh install and for
     * the seeded one.
     */
    public final String companySlug;

    public final String frontendUrl;
    /** Extra browser origins allowed by CORS besides FRONTEND_URL (comma-separated CORS_ORIGINS, as in condominios). */
    public final java.util.List<String> corsOrigins;

    public final String smtpHost;
    public final int smtpPort;
    public final String smtpUsername;
    public final String smtpPassword;
    public final boolean smtpStartTls;
    public final String smtpFromAddress;
    public final String smtpFromName;

    public final int rateLimitMaxRequests;
    public final long rateLimitWindowMs;

    public AppConfig() {
        this.port = Env.getInt("PORT", 8080);
        this.contextPath = Env.get("CONTEXT_PATH", "/api");

        // Accepts either the JDBC form (jdbc:postgresql://host:port/db, paired with DB_USERNAME/
        // DB_PASSWORD — the localhost default below) or a bare postgres(ql):// URL with embedded
        // credentials, which is what most managed Postgres providers (Render, Railway, ...) hand
        // you. Same DB_URL env var works unchanged in both places.
        String[] db = parseDbUrl(Env.get("DB_URL", "jdbc:postgresql://localhost:5432/viajes_eventos"));
        this.dbUrl = db[0];
        this.dbUsername = db[1] != null ? db[1] : Env.get("DB_USERNAME", "postgres");
        this.dbPassword = db[2] != null ? db[2] : Env.get("DB_PASSWORD", "postgres");
        this.dbPoolSize = Env.getInt("DB_POOL_SIZE", 10);

        // No insecure fallback here on purpose: a guessable/shared default would let anyone forge
        // a valid token for any user. Fail fast instead of silently signing with a known secret.
        this.jwtSecret = Env.get("JWT_SECRET", "");
        if (this.jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set (no default is provided). Generate a random secret of "
                            + "32+ bytes, e.g. `openssl rand -base64 32`, and set it via a real environment "
                            + "variable or your local config.yml override — never the shipped config.yml.");
        }
        this.jwtExpirationMs = Env.getLong("JWT_EXPIRATION_MS", 86_400_000L);

        this.companySlug = Env.get("COMPANY_SLUG", "").trim();

        this.frontendUrl = stripTrailingSlash(Env.get("FRONTEND_URL", "http://localhost:3000"));
        this.corsOrigins = java.util.Arrays.stream(Env.get("CORS_ORIGINS", "").split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).map(AppConfig::stripTrailingSlash).toList();

        // Blank SMTP_HOST (the default) makes Main wire up LoggingEmailSender instead of real SMTP.
        this.smtpHost = Env.get("SMTP_HOST", "");
        this.smtpPort = Env.getInt("SMTP_PORT", 587);
        this.smtpUsername = Env.get("SMTP_USERNAME", "");
        this.smtpPassword = Env.get("SMTP_PASSWORD", "");
        this.smtpStartTls = Boolean.parseBoolean(Env.get("SMTP_STARTTLS", "true"));
        this.smtpFromAddress = Env.get("SMTP_FROM_ADDRESS", "no-reply@viajeseventos.local");
        this.smtpFromName = Env.get("SMTP_FROM_NAME", "Busconciertos");

        this.rateLimitMaxRequests = Env.getInt("RATE_LIMIT_MAX_REQUESTS", 10);
        this.rateLimitWindowMs = Env.getLong("RATE_LIMIT_WINDOW_MS", 60_000L);
    }

    /**
     * Returns {@code [jdbcUrl, username, password]}; username/password are {@code null} when
     * {@code rawUrl} is already a JDBC URL (they come from DB_USERNAME/DB_PASSWORD instead).
     */
    /** Browsers send Origin without a trailing slash, so "https://x.onrender.com/" would never match. */
    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String[] parseDbUrl(String rawUrl) {
        if (rawUrl.startsWith("jdbc:")) {
            return new String[] { rawUrl, null, null };
        }
        URI uri = URI.create(rawUrl);
        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            username = sep >= 0 ? userInfo.substring(0, sep) : userInfo;
            password = sep >= 0 ? userInfo.substring(sep + 1) : null;
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath()
                + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
        return new String[] { jdbcUrl, username, password };
    }
}
