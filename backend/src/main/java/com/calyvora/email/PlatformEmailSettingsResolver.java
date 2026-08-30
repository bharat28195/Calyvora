package com.calyvora.email;

import com.calyvora.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * The platform's own mailbox — used for every tenant until per-tenant sending exists.
 *
 * <p>The transport is picked once at startup: an explicit {@code MAIL_PROVIDER} wins, otherwise a
 * configured Resend key means Resend, a configured SMTP username means SMTP, and a deployment with
 * neither falls back to the console. Auto-detection matters because the previous behaviour —
 * defaulting to {@code localhost:1025} — meant a deployment with no mail config appeared to send
 * mail successfully forever.
 */
@Component
public class PlatformEmailSettingsResolver implements EmailSettingsResolver {

    private static final Logger log = LoggerFactory.getLogger(PlatformEmailSettingsResolver.class);

    private final EmailSettings settings;

    public PlatformEmailSettingsResolver(
            AppProperties props,
            @Value("${calyvora.mail.provider:}") String configuredProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:0}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean starttls,
            @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean ssl) {

        String from = props.mail().from();
        String replyTo = props.mail().replyTo();
        String apiKey = props.mail().resend().apiKey();
        String apiUrl = props.mail().resend().apiUrl();

        EmailSettings.Provider provider = choose(configuredProvider, apiKey, username);

        // A provider named but not credentialed is the worst of both worlds: auto-detection is
        // skipped, so every send throws and the deployment mails nothing at all. Falling back keeps
        // the app coherent — links still reach the dev mailbox and the log — and says why, loudly.
        String missing = missingCredential(provider, apiKey, username);
        if (missing != null) {
            log.error("MAIL_PROVIDER is set to {} but {} is not configured, so no email can be sent. "
                    + "Falling back to the console transport. Set {} to deliver mail for real.",
                    provider, missing, missing);
            provider = EmailSettings.Provider.CONSOLE;
        }

        this.settings = switch (provider) {
            case RESEND -> EmailSettings.resend(from, replyTo, apiKey, apiUrl);
            case SMTP -> EmailSettings.smtp(from, replyTo, host, port, username, password, auth, starttls, ssl);
            case CONSOLE -> EmailSettings.console(from, replyTo);
        };

        log.info("Outgoing email: provider={}, from={}, endpoint={}",
                provider, from, this.settings.endpoint());
        if (provider == EmailSettings.Provider.CONSOLE) {
            log.warn("No mail provider is configured, so verification and invitation emails are only "
                    + "written to this log and never delivered. Set RESEND_API_KEY (recommended — it "
                    + "sends over HTTPS, which hosts don't block) or the MAIL_* SMTP variables.");
        }
    }

    /** The credential an explicitly named provider needs and hasn't got, or null when it can send. */
    private static String missingCredential(EmailSettings.Provider provider, String apiKey, String smtpUsername) {
        return switch (provider) {
            case RESEND -> apiKey == null || apiKey.isBlank() ? "RESEND_API_KEY" : null;
            case SMTP -> smtpUsername == null || smtpUsername.isBlank() ? "MAIL_USERNAME" : null;
            case CONSOLE -> null;
        };
    }

    @Override
    public EmailSettings resolve(UUID companyId) {
        // Per-tenant overrides will be looked up here; every tenant shares the platform mailbox today.
        return settings;
    }

    private static EmailSettings.Provider choose(String configured, String apiKey, String smtpUsername) {
        if (configured != null && !configured.isBlank()) {
            return EmailSettings.Provider.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return EmailSettings.Provider.RESEND;
        }
        if (smtpUsername != null && !smtpUsername.isBlank()) {
            return EmailSettings.Provider.SMTP;
        }
        return EmailSettings.Provider.CONSOLE;
    }
}
