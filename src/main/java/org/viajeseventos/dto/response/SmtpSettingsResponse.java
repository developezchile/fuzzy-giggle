package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;
import org.viajeseventos.model.SmtpSettings;

import java.util.Map;

/** Never echoes the password back — only whether one is set. */
public final class SmtpSettingsResponse {

    private final SmtpSettings settings;
    private final String fallbackDescription;

    /** {@code fallbackDescription}: what sends mail while these settings aren't enabled — shown on the settings screen. */
    public SmtpSettingsResponse(SmtpSettings settings, String fallbackDescription) {
        this.settings = settings;
        this.fallbackDescription = fallbackDescription;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = body();
        map.put("active", settings != null && settings.isUsable());
        map.put("fallback", fallbackDescription);
        return map;
    }

    private Map<String, Object> body() {
        Map<String, Object> map = Json.obj();
        if (settings == null) {
            map.put("configured", false);
            map.put("startTls", true);
            map.put("enabled", false);
            return map;
        }
        map.put("configured", true);
        map.put("provider", settings.getProvider());
        map.put("host", settings.getHost());
        map.put("port", settings.getPort());
        map.put("username", settings.getUsername());
        map.put("passwordSet", settings.getPassword() != null && !settings.getPassword().isBlank());
        map.put("startTls", settings.isStartTls());
        map.put("fromAddress", settings.getFromAddress());
        map.put("fromName", settings.getFromName());
        map.put("enabled", settings.isEnabled());
        map.put("updatedAt", settings.getUpdatedAt());
        return map;
    }
}
