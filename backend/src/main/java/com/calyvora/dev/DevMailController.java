package com.calyvora.dev;

import com.calyvora.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * SMTP smoke test ({@code POST /api/v1/dev/test-email?to=you@example.com}).
 *
 * <p>Registration and invites send through {@link com.calyvora.email.SmtpEmailService}, which
 * deliberately swallows send failures so a mail outage can't roll back a successful signup. Useful
 * in production, terrible for diagnosis: a misconfigured host looks identical to a delivered mail.
 * This endpoint runs the same send and hands back the actual provider error, plus the effective
 * settings (never the password), so a bad host/port/credential is obvious in one call.
 *
 * <p>Disabled under {@code prod} along with the rest of {@code /api/v1/dev/**}.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
public class DevMailController {

    private final JavaMailSender mailSender;
    private final String from;
    private final String host;
    private final int port;
    private final String username;
    private final boolean auth;
    private final boolean starttls;
    private final boolean ssl;

    public DevMailController(
            JavaMailSender mailSender,
            AppProperties props,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:0}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean starttls,
            @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean ssl) {
        this.mailSender = mailSender;
        this.from = props.mail().from();
        this.host = host;
        this.port = port;
        this.username = username;
        this.auth = auth;
        this.starttls = starttls;
        this.ssl = ssl;
    }

    @PostMapping("/test-email")
    public Result testEmail(@RequestParam String to) {
        Config config = new Config(host, port, username, from, auth, starttls, ssl);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Orbit SMTP test");
            message.setText("If you're reading this, Orbit's outgoing email is configured correctly.\n\n"
                    + "Sent at " + Instant.now() + ".");
            mailSender.send(message);
            return new Result(true, null, config);
        } catch (Exception ex) {
            // Report rather than rethrow: the whole point is to surface the provider's own wording.
            return new Result(false, describe(ex), config);
        }
    }

    /** Root-cause message — JavaMail buries the useful text (auth refused, connect timeout) in the cause. */
    private static String describe(Throwable ex) {
        StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            sb.append(" | caused by ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }

    public record Result(boolean sent, String error, Config config) {}

    /** Effective SMTP settings. No password — this endpoint is unauthenticated. */
    public record Config(String host, int port, String username, String from,
                         boolean auth, boolean starttls, boolean ssl) {}
}
