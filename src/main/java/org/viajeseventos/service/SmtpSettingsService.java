package org.viajeseventos.service;

import org.viajeseventos.dto.request.SmtpSettingsRequest;
import org.viajeseventos.email.ConfigurableEmailSender;
import org.viajeseventos.email.EmailTemplates;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.model.SmtpSettings;
import org.viajeseventos.repository.SmtpSettingsRepository;

/** Outgoing mail settings (SETTINGS module) — same behavior as doscolas' SmtpSettingsService. */
public final class SmtpSettingsService {

    private final SmtpSettingsRepository settingsRepository;
    private final ConfigurableEmailSender emailSender;

    public SmtpSettingsService(SmtpSettingsRepository settingsRepository, ConfigurableEmailSender emailSender) {
        this.settingsRepository = settingsRepository;
        this.emailSender = emailSender;
    }

    public SmtpSettings get() {
        return settingsRepository.find().orElse(null);
    }

    public String fallbackDescription() {
        return emailSender.fallbackDescription();
    }

    public SmtpSettings update(SmtpSettingsRequest req) {
        SmtpSettings existing = settingsRepository.find().orElse(null);
        SmtpSettings settings = new SmtpSettings();
        settings.setProvider(req.provider);
        settings.setHost(req.host);
        settings.setPort(req.port);
        settings.setUsername(req.username);
        // Blank password on update means "keep the one already saved" — the response never sends
        // it back, so the form can't round-trip it.
        settings.setPassword((req.password == null || req.password.isBlank())
                ? (existing != null ? existing.getPassword() : null)
                : req.password);
        settings.setStartTls(req.startTls);
        settings.setFromAddress(req.fromAddress);
        settings.setFromName(req.fromName);
        settings.setEnabled(req.enabled);
        return settingsRepository.save(settings);
    }

    /** Sends through the saved settings even if not enabled yet, so they can be verified before switching on. */
    public void sendTest(String to) {
        SmtpSettings settings = settingsRepository.find().orElse(null);
        if (settings == null || settings.getHost() == null || settings.getHost().isBlank()
                || settings.getPort() == null || settings.getFromAddress() == null) {
            throw new BusinessRuleException("Guarda primero un servidor, puerto y remitente para enviar la prueba.");
        }
        try {
            emailSender.sendTest(settings, to, "Correo de prueba — Busconciertos",
                    EmailTemplates.testEmail(settings.getProvider()));
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            throw new BusinessRuleException("No se pudo enviar el correo de prueba: " + root.getMessage());
        }
    }
}
