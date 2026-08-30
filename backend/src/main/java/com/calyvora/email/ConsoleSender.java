package com.calyvora.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prints the message to the log instead of sending it — the zero-config local-dev transport, so a
 * developer can copy a verification or invite link straight out of the backend console with no SMTP
 * server, no Docker and no API key.
 */
@Component
public class ConsoleSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSender.class);

    @Override
    public EmailSettings.Provider provider() {
        return EmailSettings.Provider.CONSOLE;
    }

    @Override
    public void send(EmailSettings settings, String to, String subject, String body, String html) {
        log.info("""

                ┌───────────────────────────────────────────────────────────────
                │ [DEV EMAIL] {}
                │ to:   {}
                │ {}
                └───────────────────────────────────────────────────────────────""", subject, to, body);
    }
}
