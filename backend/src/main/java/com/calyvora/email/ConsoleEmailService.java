package com.calyvora.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local-dev email that prints links to the console instead of sending SMTP. Active under the
 * {@code embedded} profile (no Docker/Mailpit), so you can copy the verification / invite link
 * straight from the backend log. {@code @Primary} to win over {@link SmtpEmailService}.
 *
 * <p>Stands down as soon as a real mailbox is configured ({@code MAIL_USERNAME}), so the genuine
 * SMTP path — the one a deployment actually runs — can be exercised locally end to end instead of
 * only in the hosted environment.
 */
@Service
@Primary
@Profile("embedded")
@ConditionalOnExpression("'${spring.mail.username:}'.isEmpty()")
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    private final DevMailbox mailbox;

    public ConsoleEmailService(DevMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        banner("VERIFY EMAIL", to, verificationUrl);
        mailbox.record(to, "Verify your Calyvora email", verificationUrl);
    }

    @Override
    public void sendInvitationEmail(String to, String companyName, String acceptUrl) {
        banner("INVITE → " + companyName, to, acceptUrl);
        mailbox.record(to, "You're invited to " + companyName + " on Calyvora", acceptUrl);
    }

    private void banner(String kind, String to, String url) {
        log.info("""

                ┌───────────────────────────────────────────────────────────────
                │ [DEV EMAIL] {}
                │ to:   {}
                │ link: {}
                └───────────────────────────────────────────────────────────────""", kind, to, url);
    }
}
