package com.calyvora.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates opaque single-use tokens (email verification, invitations, refresh tokens) and their
 * at-rest SHA-256 hashes. The raw token is returned once (goes into the email link / cookie); only
 * the {@link #sha256(String)} hash is ever persisted (Sprint1 §5 isolation note).
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_BYTES = 32;   // 256-bit

    private TokenGenerator() {}

    /** URL-safe, no-padding base64 of 256 random bits. */
    public static String rawToken() {
        byte[] bytes = new byte[DEFAULT_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 (64 chars) — matches the {@code varchar(64)} token_hash columns. */
    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
