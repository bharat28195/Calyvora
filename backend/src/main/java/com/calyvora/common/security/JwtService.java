package com.calyvora.common.security;

import com.calyvora.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and verifies short-lived access tokens (HS256 — SD-5; RS256 fast-follow in Sprint 2).
 * Claims: {@code sub}=userId, {@code companyId}, {@code role}, {@code email}, {@code jti},
 * {@code iss}, {@code exp}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(AppProperties props) {
        AppProperties.Jwt jwt = props.security().jwt();
        this.key = Keys.hmacShaKeyFor(decodeSecret(jwt.secret()));
        this.issuer = jwt.issuer();
        this.accessTtlSeconds = jwt.accessTokenTtl().toSeconds();
    }

    /** Base64 first (the recommended form); fall back to raw UTF-8 bytes for convenience. */
    private static byte[] decodeSecret(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64 — use raw bytes
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 bytes)");
        }
        return raw;
    }

    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(principal.userId().toString())
                .id(UUID.randomUUID().toString())
                .claim("companyId", principal.companyId().toString())
                .claim("role", principal.role())
                .claim("email", principal.email())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Parse and verify a token.
     * @throws JwtException if the token is invalid, tampered, or expired.
     */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("companyId", String.class)),
                claims.get("role", String.class),
                claims.get("email", String.class)
        );
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
