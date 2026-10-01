package com.calyvora.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use one-time code for resetting a forgotten password (PD-23). Only the SHA-256 hash is
 * stored — the code has the full power of a password while it lives, so a leaked database must not
 * hand over working ones.
 */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    /** Wrong guesses allowed before the code is dead. See {@link #isExhausted()}. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(UUID id, UUID userId, String codeHash, String channel, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.channel = channel;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /**
     * Six digits is one chance in a million per guess — but only a few thousand guesses from even
     * odds, which a script does in seconds. The cap is what makes a short code safe to use at all.
     */
    public boolean isExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    /** Usable right now: not spent, not expired, not guessed at too many times. */
    public boolean isLive(Instant now) {
        return !isConsumed() && !isExpired(now) && !isExhausted();
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void consume() {
        consumedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public String getChannel() { return channel; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
}
