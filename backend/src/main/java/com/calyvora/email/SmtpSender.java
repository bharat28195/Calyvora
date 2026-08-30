package com.calyvora.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    public void send(EmailSettings settings, String to, String subject, String body, String html) throws Exception {
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

        // multipart/alternative: the same message as text and HTML, the client choosing. Sending only
        // HTML costs deliverability — filters read the text part, and its absence counts against you.
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(settings.from(), EmailIdentity.DISPLAY_NAME, "UTF-8"));
        helper.setTo(to);
        helper.setSubject(subject);
        if (html == null || html.isBlank()) {
            helper.setText(body, false);
        } else {
            helper.setText(body, html);
        }
        if (settings.replyTo() != null && !settings.replyTo().isBlank()) {
            helper.setReplyTo(settings.replyTo());
        }
        sender.send(message);
    }
}
