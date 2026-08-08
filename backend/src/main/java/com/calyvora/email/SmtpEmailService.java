package com.calyvora.email;

import com.calyvora.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text email via SMTP (Mailpit locally). Failures are logged, not propagated — a mail
 * hiccup must not roll back a successful registration/invite (the user can use "resend").
 *
 * <p>When the dev mailbox is present (the {@code embedded} profile) every link is also captured
 * there, so {@code /dev/mailbox} keeps working whichever sender is active. Recording happens before
 * the send and regardless of its outcome — the link is most needed precisely when delivery failed.
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final ObjectProvider<DevMailbox> mailbox;

    public SmtpEmailService(JavaMailSender mailSender, AppProperties props,
                            ObjectProvider<DevMailbox> mailbox) {
        this.mailSender = mailSender;
        this.from = props.mail().from();
        this.mailbox = mailbox;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        String subject = "Verify your Calyvora email";
        record(to, subject, verificationUrl);
        send(to, subject,
                "Welcome to Calyvora!\n\nConfirm your email address to activate your account:\n"
                        + verificationUrl + "\n\nThis link expires in 24 hours.");
    }

    @Override
    public void sendInvitationEmail(String to, String companyName, String acceptUrl) {
        String subject = "You're invited to join " + companyName + " on Calyvora";
        record(to, subject, acceptUrl);
        send(to, subject,
                "You've been invited to join " + companyName + " on Calyvora.\n\n"
                        + "Accept your invitation and set a password:\n" + acceptUrl
                        + "\n\nThis invitation expires in 7 days.");
    }

    /** No-op outside the {@code embedded} profile, where no {@link DevMailbox} bean exists. */
    private void record(String to, String subject, String link) {
        DevMailbox box = mailbox.getIfAvailable();
        if (box != null) {
            box.record(to, subject, link);
        }
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("Sent email '{}' to {}", subject, to);
        } catch (Exception ex) {
            // A mail hiccup must never roll back a successful registration/invite (user can resend).
            // Catch broadly: JavaMail connect failures surface as unchecked MailConnectException too.
            log.warn("Failed to send email '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}
