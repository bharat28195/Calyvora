package com.calyvora.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes a refresh-token family in its <em>own</em> transaction. Reuse detection must burn the
 * family even though the surrounding request then throws 401 — a plain call would be rolled back
 * with that exception. Split into a separate bean so the {@code REQUIRES_NEW} proxy actually applies.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId) {
        return repository.revokeFamily(familyId, Instant.now());
    }
}
