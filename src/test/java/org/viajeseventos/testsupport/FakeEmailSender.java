package org.viajeseventos.testsupport;

import org.viajeseventos.email.EmailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captures sent emails instead of delivering them, so tests can pull the verification/reset
 *  token out of the link without needing a real SMTP server. */
public final class FakeEmailSender implements EmailSender {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[?&]token=([^&\"\\s]+)");

    public record SentEmail(String to, String subject, String htmlBody) {
    }

    private final List<SentEmail> sent = new ArrayList<>();

    @Override
    public synchronized void send(String to, String subject, String htmlBody) {
        sent.add(new SentEmail(to, subject, htmlBody));
    }

    public synchronized SentEmail lastSentTo(String to) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).to().equals(to)) return sent.get(i);
        }
        throw new AssertionError("No email was sent to " + to);
    }

    public String lastTokenSentTo(String to) {
        Matcher m = TOKEN_PATTERN.matcher(lastSentTo(to).htmlBody());
        if (!m.find()) throw new AssertionError("No token found in email body for " + to);
        return m.group(1);
    }
}
