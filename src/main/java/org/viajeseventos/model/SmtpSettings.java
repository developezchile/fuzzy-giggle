package org.viajeseventos.model;

import java.time.LocalDateTime;

/** Admin-editable SMTP configuration (e.g. Maileroo), stored as the single row in
 *  {@code smtp_settings} — see {@link org.viajeseventos.email.ConfigurableEmailSender}. */
public final class SmtpSettings {

    private String provider;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private boolean startTls = true;
    private String fromAddress;
    private String fromName;
    private boolean enabled = false;
    private LocalDateTime updatedAt;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** True once there's enough to actually attempt a send. */
    public boolean isUsable() {
        return enabled && host != null && !host.isBlank() && port != null
                && fromAddress != null && !fromAddress.isBlank();
    }
}
