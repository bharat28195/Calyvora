package com.calyvora.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Classic SMTP. The mail session is built from the {@link EmailSettings} passed in rather than from
 * a single application-wide {@code JavaMailSender} bean, so a tenant's own mailbox can be used later
 * without this class changing.
 *
 * <p>Every timeout is set explicitly: JavaMail defaults to waiting forever, and registration sends
 * inline — one unreachable mail host would otherwise pin a request thread until the client gives up.
 */
@Component
public class SmtpSender implements EmailSender {

    private static final String TIMEOUT_MS = "10000";

    @Override
    public EmailSettings.Provider provider() {
        return EmailSettings.Provider.SMTP;
    }

    @Override
    public void send(EmailSettings settings, String to, String subject, String body) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        if (settings.username() != null && !settings.username().isBlank()) {
            sender.setUsername(settings.username());
            sender.setPassword(settings.password());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(settings.auth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.starttls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.ssl()));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
