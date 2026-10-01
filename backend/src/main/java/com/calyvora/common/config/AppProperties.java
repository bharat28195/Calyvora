package com.calyvora.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Strongly-typed binding for the {@code calyvora.*} configuration tree (application.yml).
 * Keeping config typed (not scattered {@code @Value}) makes the security posture auditable
 * in one place.
 */
@ConfigurationProperties(prefix = "calyvora")
public record AppProperties(
        String frontendBaseUrl,
        List<String> corsAllowedOrigins,
        Mail mail,
        Security security,
        Trial trial
) {
    /**
     * Where "someone wants a free trial" lands (PD-21).
     *
     * @param notifyEmail the vendor's own inbox. Blank falls back to the platform owner's login
     *                    address, so a deployment that never configured this still tells someone.
     */
    public record Trial(String notifyEmail) {}

    /**
     * Outgoing mail. {@code provider} pins the transport ({@code resend} / {@code smtp} /
     * {@code console}); left blank it is inferred from whichever credentials are present.
     */
    /**
     * @param otpFrom the address a one-time code is sent from. Separate from {@code from} because a
     *                code is the one message where the sender should visibly be a machine: it is the
     *                mail an attacker most wants to imitate, so a reader who learns codes always come
     *                from noreply has something to check a forgery against.
     */
    public record Mail(String provider, String from, String replyTo, String otpFrom, Resend resend) {}

    /** Resend's HTTPS API — the transport that survives hosts which block outbound SMTP. */
    public record Resend(String apiKey, String apiUrl) {}

    public record Security(Jwt jwt, Refresh refresh, Verification verification, Invitation invitation,
                          Registration registration) {}

    /**
     * @param open whether anyone may create a workspace from the public {@code /auth/register}
     *             endpoint. <b>False by default</b> (PD-21): Orbit is sold by a person, so the trial
     *             button raises a request the vendor approves, and no account exists until then.
     *             Turning this on restores open self-signup — which also means anyone who finds the
     *             URL gets a live workspace, so it is a deliberate commercial decision, not a default.
     */
    public record Registration(boolean open) {}

    /**
     * RS256 asymmetric signing (SD-5). {@code activeKid} names the key used to sign new tokens;
     * every entry in {@code keys} is trusted for verification, so rotation is: add the new key,
     * flip {@code activeKid}, and keep the old key around until its longest-lived token expires.
     * When {@code keys} is empty an ephemeral in-memory keypair is generated at startup — fine for
     * local/dev/test, never for a shared environment (tokens don't survive a restart).
     */
    public record Jwt(String activeKid, List<RsaKey> keys, Duration accessTokenTtl, String issuer) {}

    /** One RSA keypair. {@code privateKeyPem} is optional on retired keys kept only for verification. */
    public record RsaKey(String kid, String privateKeyPem, String publicKeyPem) {}

    public record Refresh(Duration ttl, String cookieName, boolean cookieSecure) {}

    /**
     * @param required when false (the default) a new workspace is usable the moment it's created —
     *                 the signup becomes an active company ADMIN and can log straight in. Turn it on
     *                 once outgoing mail is proven, to make people confirm they own the address.
     */
    public record Verification(Duration ttl, boolean required) {}

    public record Invitation(Duration ttl) {}
}
