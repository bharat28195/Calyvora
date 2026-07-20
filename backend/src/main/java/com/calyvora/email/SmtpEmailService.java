package com.calyvora.email;

import com.calyvora.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text email via SMTP (Mailpit locally). Failures are logged, not propagated — a mail
 * hiccup must not roll back a successful registration/invite (the user can use "resend").
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.from = props.mail().from();
    }

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        send(to, "Verify your Calyvora email",
                "Welcome to Calyvora!\n\nConfirm your email address to activate your account:\n"
                        + verificationUrl + "\n\nThis link expires in 24 hours.");
    }

    @Override
    public void sendInvitationEmail(String to, String companyName, String acceptUrl) {
        send(to, "You're invited to join " + companyName + " on Calyvora",
                "You've been invited to join " + companyName + " on Calyvora.\n\n"
                        + "Accept your invitation and set a password:\n" + acceptUrl
                        + "\n\nThis invitation expires in 7 days.");
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
