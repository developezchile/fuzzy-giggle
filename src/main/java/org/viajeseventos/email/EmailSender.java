package org.viajeseventos.email;

/** Sends a single HTML email. Implementations must not let a delivery failure surface as a 500 to
 *  the caller of whatever triggered the email — callers should catch and log, not propagate. */
public interface EmailSender {

    /**
     * Who an email goes out on behalf of: the company's name as the From display name, its contact
     * address as Reply-To. The From address itself stays the platform's SMTP one — a relay only
     * delivers mail from domains it's verified for.
     */
    record OnBehalfOf(String name, String replyTo) {
    }

    /** {@code onBehalfOf} may be null: the platform's own name, no Reply-To. */
    void send(String to, String subject, String htmlBody, OnBehalfOf onBehalfOf);

    default void send(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, null);
    }
}
