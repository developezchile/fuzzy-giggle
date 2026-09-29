-- Admin-configurable SMTP settings (e.g. Maileroo), stored so they can be changed at runtime
-- from the SETTINGS module instead of only via SMTP_* env vars. Single-row table: id is always 1.

CREATE TABLE IF NOT EXISTS smtp_settings (
    id           INTEGER PRIMARY KEY DEFAULT 1,
    provider     VARCHAR(50),
    host         VARCHAR(255),
    port         INTEGER,
    username     VARCHAR(255),
    password     VARCHAR(255),
    start_tls    BOOLEAN NOT NULL DEFAULT TRUE,
    from_address VARCHAR(255),
    from_name    VARCHAR(255),
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP,
    CONSTRAINT chk_smtp_settings_singleton CHECK (id = 1)
);
