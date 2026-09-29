package org.viajeseventos.email;

import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.model.SmtpSettings;
import org.viajeseventos.repository.SmtpSettingsRepository;

/**
 * The {@link EmailSender} actually wired into the app. Prefers the admin-configured SMTP settings
 * (SETTINGS module, backed by {@code smtp_settings} — e.g. Maileroo),
 * checked fresh on every send so a change takes effect immediately with no restart. Falls back to
 * whatever {@code SMTP_*} env vars produced at startup (real SMTP, or {@link LoggingEmailSender})
 * when no admin-configured settings are enabled.
 *
 * <p>Email volume here is low (auth notifications, not bulk sending), so building a fresh
 * {@link SmtpEmailSender} per send is deliberate — simpler than caching a session and invalidating
 * it when settings change.
 */
public final class ConfigurableEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(ConfigurableEmailSender.class);

    private final SmtpSettingsRepository settingsRepository;
    private final EmailSender fallback;
    private final String fallbackDescription;

    /** {@code fallbackDescription} says what {@code fallback} is (e.g. the SMTP host from config) for the settings screen. */
    public ConfigurableEmailSender(SmtpSettingsRepository settingsRepository, EmailSender fallback,
                                   String fallbackDescription) {
        this.settingsRepository = settingsRepository;
        this.fallback = fallback;
        this.fallbackDescription = fallbackDescription;
    }

    public String fallbackDescription() {
        return fallbackDescription;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        SmtpSettings settings = settingsRepository.find().orElse(null);
        if (settings != null && settings.isUsable()) {
            senderFor(settings).send(to, subject, htmlBody);
            return;
        }
        fallback.send(to, subject, htmlBody);
    }

    private EmailSender senderFor(SmtpSettings settings) {
        return new SmtpEmailSender(settings.getHost(), settings.getPort(), settings.getUsername(),
                settings.getPassword(), settings.isStartTls(), settings.getFromAddress(),
                settings.getFromName() != null ? settings.getFromName() : "Viajes a Eventos");
    }

    /** Used by the "send test email" action — sends through the given settings regardless
     *  of whether they're saved/enabled yet, so an admin can verify before flipping it on. */
    public void sendTest(SmtpSettings settings, String to, String subject, String htmlBody) {
        log.info("Sending test email via {}:{} to {}", settings.getHost(), settings.getPort(), to);
        senderFor(settings).send(to, subject, htmlBody);
    }
}
