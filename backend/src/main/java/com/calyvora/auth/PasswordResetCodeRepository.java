package com.calyvora.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /**
     * Every code ever issued to this user, newest first. The service takes the newest live one —
     * looking a code up by its hash instead would let an attacker who guessed any user's code use it,
     * since a six-digit space collides across accounts constantly.
     */
    List<PasswordResetCode> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Requests in a window, for throttling: one address must not be able to send itself 100 emails. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
