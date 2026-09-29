package org.viajeseventos.email;

/** Sends a single HTML email. Implementations must not let a delivery failure surface as a 500 to
 *  the caller of whatever triggered the email — callers should catch and log, not propagate. */
public interface EmailSender {
    void send(String to, String subject, String htmlBody);
}
