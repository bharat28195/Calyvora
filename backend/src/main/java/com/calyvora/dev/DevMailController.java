package com.calyvora.dev;

import com.calyvora.email.DispatchingEmailService;
import com.calyvora.email.EmailSettings;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Mail smoke test ({@code POST /api/v1/dev/test-email?to=you@example.com}).
 *
 * <p>Registration and invites send through {@link DispatchingEmailService}, which deliberately
 * swallows send failures so a mail outage can't roll back a successful signup. Useful in production,
 * terrible for diagnosis: a misconfigured mailbox looks identical to a delivered mail. This endpoint
 * runs the same send through the same transport and hands back the actual provider error, plus the
 * settings in effect (never the credential), so a bad key/host/password is obvious in one call.
 *
 * <p>Disabled under {@code prod} along with the rest of {@code /api/v1/dev/**}.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
public class DevMailController {

    private final DispatchingEmailService emailService;

    public DevMailController(DispatchingEmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/test-email")
    public Result testEmail(@RequestParam String to) {
        EmailSettings settings = emailService.currentSettings();
        Config config = new Config(
                settings.endpoint(), settings.from(), settings.username(),
                settings.auth(), settings.starttls(), settings.ssl());

        // The console transport always "succeeds" — it writes to the log. Reporting that as sent
        // would be the exact lie this endpoint exists to catch, so call it what it is.
        if (settings.provider() == EmailSettings.Provider.CONSOLE) {
            return new Result(false, settings.provider().name(),
                    "No mail provider is configured, so nothing was delivered — the message was only "
                            + "written to the server log. Set RESEND_API_KEY (recommended) or the "
                            + "MAIL_* SMTP variables.", config);
        }
        try {
            emailService.sendOrThrow(to, "Orbit email test",
                    "If you're reading this, Orbit's outgoing email is configured correctly.\n\n"
                            + "Sent at " + Instant.now() + ".");
            return new Result(true, settings.provider().name(), null, config);
        } catch (Exception ex) {
            // Report rather than rethrow: the whole point is to surface the provider's own wording.
            return new Result(false, settings.provider().name(), DispatchingEmailService.describe(ex), config);
        }
    }

    public record Result(boolean sent, String provider, String error, Config config) {}

    /**
     * Effective mail settings. Never the API key or password — this endpoint is unauthenticated, so
     * it must not become a way to read a secret back out of the environment.
     */
    public record Config(String endpoint, String from, String username,
                         boolean auth, boolean starttls, boolean ssl) {}
}
