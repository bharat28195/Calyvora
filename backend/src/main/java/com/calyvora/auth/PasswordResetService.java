package com.calyvora.auth;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.util.TokenGenerator;
import com.calyvora.email.EmailService;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Forgotten passwords (PD-23). Ask for a code, then use it to set a new password.
 *
 * <p>Before this, there was no way back into an account: an admin had to set a new password by hand,
 * and the platform owner had nobody to ask at all. Every earlier change that handled credentials —
 * approving a trial, moving the owner account — had to pass passwords around out of band precisely
 * because this did not exist.
 *
 * <p>Email only. SMS was the original request and was dropped on cost: Indian transactional SMS is
 * charged per message and needs DLT registration before a gateway delivers anything, while email is
 * already configured and free at this volume. The code is a six-digit number rather than a link
 * because that is what an SMS would carry, so adding the channel later needs a sender, not a redesign.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Long enough to fetch a mail on another device, short enough that a stolen code goes stale. */
    static final Duration TTL = Duration.ofMinutes(15);
    /** Requests allowed per account per hour, so this endpoint cannot be used to mail-bomb someone. */
    static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final Duration THROTTLE_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ResetAttemptRecorder attemptRecorder;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetCodeRepository codeRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                ResetAttemptRecorder attemptRecorder) {
        this.attemptRecorder = attemptRecorder;
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Send a code, if there is anywhere to send it.
     *
     * <p>Always succeeds from the caller's point of view, whatever happens inside. An unknown address
     * must be indistinguishable from a known one — otherwise this endpoint becomes a way to ask
     * "does this person have an account here?", one address at a time, and the answer is worth having
     * for anyone building a list to attack.
     *
     * <p>For the same reason it reports nothing about throttling: a caller who could tell "too many
     * requests" from "sent" would learn the address exists.
     */
    @Transactional
    public void requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (email == null || email.isEmpty()) {
            return;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Logged, not answered: a spike here is worth seeing, and the caller learns nothing.
            log.info("Password reset requested for an address with no account.");
            return;
        }
        // A disabled account must not be reactivatable by whoever holds the mailbox.
        if (user.getStatus() == UserStatus.DISABLED) {
            log.info("Password reset requested for a disabled account; ignoring.");
            return;
        }
        if (codeRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), Instant.now().minus(THROTTLE_WINDOW)) >= MAX_REQUESTS_PER_HOUR) {
            log.warn("Password reset throttled for user {}.", user.getId());
            return;
        }

        // Asking again invalidates the previous code. Two live codes would double an attacker's
        // chances for no benefit to anyone — and people ask again precisely because the first mail
        // has not arrived, so the newest is the one they will type.
        for (PasswordResetCode previous : codeRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (!previous.isConsumed()) {
                previous.consume();
            }
        }

        String code = sixDigits();
        codeRepository.save(new PasswordResetCode(UUID.randomUUID(), user.getId(),
                TokenGenerator.sha256(code), "EMAIL", Instant.now().plus(TTL)));

        emailService.sendPasswordResetCode(user.getEmail(), code, TTL.toMinutes());
    }

    /**
     * Spend a code and set the new password.
     *
     * <p>Wrong codes are counted against the live code, not merely refused, so guessing six digits
     * costs five tries rather than a million. Every error out of here says the same thing, for the
     * same reason the request half does: "no account" and "wrong code" must look identical.
     */
    @Transactional
    public void reset(String rawEmail, String code, String newPassword) {
        String email = normalize(rawEmail);
        User user = email == null ? null : userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            throw invalid();
        }

        Instant now = Instant.now();
        PasswordResetCode live = codeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(c -> c.isLive(now))
                .findFirst()
                .orElseThrow(PasswordResetService::invalid);

        if (!TokenGenerator.sha256(code == null ? "" : code.trim()).equals(live.getCodeHash())) {
            // In its own transaction, or the throw below rolls the count back and the cap never
            // bites — see ResetAttemptRecorder.
            attemptRecorder.recordFailure(live.getId());
            throw invalid();
        }

        live.consume();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Verification exists to prove the address is real, and using a code sent to it just did.
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(now);
        }

        // Sign every session out. The likeliest reason someone resets is that a session is somewhere
        // it should not be; leaving live refresh tokens behind would mean the new password changes
        // nothing for whoever already holds one.
        int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), now);
        log.info("Password reset for user {}; {} session(s) revoked.", user.getId(), revoked);
    }

    /**
     * One message for every failure. Distinguishing "no such account" from "wrong code" from "expired"
     * would tell an attacker which of those to fix.
     */
    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_ERROR,
                "That code isn't valid. It may have expired or already been used — ask for a new one.");
    }

    /** Zero-padded so every code is six characters; "004821" must not be typed as "4821". */
    private static String sixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Exposed for tests that need to age a code without waiting a quarter of an hour. */
    List<PasswordResetCode> codesFor(UUID userId) {
        return codeRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
