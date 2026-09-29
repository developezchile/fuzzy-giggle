package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/** Body of {@code PUT /settings/smtp}. Host, port and sender are only required once enabled. */
public final class SmtpSettingsRequest {

    public final String provider;
    public final String host;
    public final Integer port;
    public final String username;
    /** Null/blank means "leave the currently saved password as-is" — see SmtpSettingsService. */
    public final String password;
    public final boolean startTls;
    public final String fromAddress;
    public final String fromName;
    public final boolean enabled;

    private SmtpSettingsRequest(String provider, String host, Integer port, String username, String password,
                                 boolean startTls, String fromAddress, String fromName, boolean enabled) {
        this.provider = provider;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.startTls = startTls;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.enabled = enabled;
    }

    public static SmtpSettingsRequest fromJson(Map<String, Object> json) {
        String provider = optStr(json, "provider");
        String host = optStr(json, "host");
        String username = optStr(json, "username");
        String password = str(json, "password");
        String fromAddress = optStr(json, "fromAddress");
        String fromName = optStr(json, "fromName");

        var errors = newErrors();
        Long port = longVal(errors, json, "port");
        if (port != null && (port < 1 || port > 65535)) errors.put("port", "debe estar entre 1 y 65535");
        Boolean startTls = boolVal(errors, json, "startTls");
        Boolean enabled = boolVal(errors, json, "enabled");
        boolean enabledValue = enabled != null && enabled;

        if (enabledValue) {
            notBlank(errors, "host", host);
            notNull(errors, "port", port);
            notBlank(errors, "fromAddress", fromAddress);
        }
        email(errors, "fromAddress", fromAddress);
        maxLength(errors, "host", host, 255);
        maxLength(errors, "fromName", fromName, 255);
        check(errors);

        return new SmtpSettingsRequest(provider, host, port != null ? port.intValue() : null, username, password,
                startTls == null || startTls, fromAddress, fromName, enabledValue);
    }
}
