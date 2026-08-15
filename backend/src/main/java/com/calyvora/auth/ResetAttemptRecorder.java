package com.calyvora.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Counts a wrong guess in its <em>own</em> transaction.
 *
 * <p>Without this the cap does not exist. The obvious code — increment the counter, then throw to
 * reject the guess — loses the increment, because the exception rolls the whole transaction back
 * including the count. Every wrong guess would therefore be the first wrong guess, and a six-digit
 * code, which is only safe because the number of tries is small, becomes brute-forceable at leisure.
 *
 * <p>A separate bean because {@code REQUIRES_NEW} is applied by the proxy: calling a method on
 * {@code this} would bypass it entirely and silently restore the bug. The same reasoning, and the
 * same shape, as {@link RefreshTokenRevoker}.
 */
@Component
class ResetAttemptRecorder {

    private final PasswordResetCodeRepository repository;

    ResetAttemptRecorder(PasswordResetCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID codeId) {
        repository.findById(codeId).ifPresent(PasswordResetCode::recordFailedAttempt);
    }
}
