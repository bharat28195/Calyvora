package com.calyvora.common.security;

import com.calyvora.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and verifies short-lived access tokens (RS256 asymmetric signing — SD-5).
 * The signing key's {@code kid} is written into the JWS header so a verifier can pick the matching
 * public key from the {@link JwtKeyStore}; that indirection is what enables key rotation and lets
 * other services verify tokens from the JWKS endpoint without sharing a secret.
 *
 * <p>Claims: {@code sub}=userId, {@code companyId}, {@code role}, {@code email}, {@code jti},
 * {@code iss}, {@code exp}.
 */
@Service
public class JwtService {

    private final JwtKeyStore keyStore;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(JwtKeyStore keyStore, AppProperties props) {
        AppProperties.Jwt jwt = props.security().jwt();
        this.keyStore = keyStore;
        this.issuer = jwt.issuer();
        this.accessTtlSeconds = jwt.accessTokenTtl().toSeconds();
    }

    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyStore.activeKid()).and()
                .issuer(issuer)
                .subject(principal.userId().toString())
                .id(UUID.randomUUID().toString())
                .claim("companyId", principal.companyId().toString())
                .claim("role", principal.role())
                .claim("email", principal.email())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(keyStore.signingKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parse and verify a token, selecting the public key by the JWS {@code kid} header.
     * @throws JwtException if the token is invalid, tampered, expired, or references an unknown key.
     */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(ProtectedHeader header) {
                        Key key = keyStore.verificationKey(header.getKeyId());
                        if (key == null) {
                            throw new JwtException("Unknown signing key id: " + header.getKeyId());
                        }
                        return key;
                    }
                })
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
