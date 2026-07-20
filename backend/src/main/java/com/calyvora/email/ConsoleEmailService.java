package com.calyvora.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local-dev email that prints links to the console instead of sending SMTP. Active only under the
 * {@code embedded} profile (no Docker/Mailpit), so you can copy the verification / invite link
 * straight from the backend log. {@code @Primary} to win over {@link SmtpEmailService}.
 */
@Service
@Primary
@Profile("embedded")
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        banner("VERIFY EMAIL", to, verificationUrl);
    }

    @Override
    public void sendInvitationEmail(String to, String companyName, String acceptUrl) {
        banner("INVITE → " + companyName, to, acceptUrl);
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
