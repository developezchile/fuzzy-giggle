package org.viajeseventos.email;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/** Real delivery via SMTP (Jakarta Mail / Angus). Works against Gmail, SES, Mailgun, etc. — any
 *  provider's SMTP relay — so there's no vendor SDK to swap out later, just env vars. */
public final class SmtpEmailSender implements EmailSender {

    private final Session session;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(String host, int port, String username, String password, boolean startTls,
                            String fromAddress, String fromName) {
        this.fromAddress = fromAddress;
        this.fromName = fromName;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        boolean auth = username != null && !username.isBlank();
        props.put("mail.smtp.auth", String.valueOf(auth));

        this.session = auth
                ? Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                })
                : Session.getInstance(props);
    }

    @Override
    public void send(String to, String subject, String htmlBody, OnBehalfOf onBehalfOf) {
        try {
            MimeMessage message = new MimeMessage(session);
            boolean named = onBehalfOf != null && onBehalfOf.name() != null && !onBehalfOf.name().isBlank();
            message.setFrom(new InternetAddress(fromAddress, named ? onBehalfOf.name() : fromName, "UTF-8"));
            if (onBehalfOf != null && onBehalfOf.replyTo() != null && !onBehalfOf.replyTo().isBlank()) {
                message.setReplyTo(InternetAddress.parse(onBehalfOf.replyTo()));
            }
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlBody, "text/html; charset=UTF-8");
            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }
}
