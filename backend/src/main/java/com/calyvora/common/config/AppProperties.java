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
        Security security
) {
    public record Mail(String from) {}

    public record Security(Jwt jwt, Refresh refresh, Verification verification, Invitation invitation) {}

    public record Jwt(String secret, Duration accessTokenTtl, String issuer) {}

    public record Refresh(Duration ttl, String cookieName, boolean cookieSecure) {}

    public record Verification(Duration ttl) {}

    public record Invitation(Duration ttl) {}
}
