package com.calyvora.auth;

import com.calyvora.common.config.AppProperties;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.identity.UserRepository;
import com.calyvora.common.error.UnauthorizedException;
import com.calyvora.common.util.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues, rotates, and revokes refresh tokens (SD-5). Each login starts a token <em>family</em>;
 * refreshing rotates to a new token in the same family and revokes the old one. Presenting an
 * already-rotated (revoked) token is treated as theft — the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenRevoker revoker;
    private final UserRepository userRepository;
    private final CompanySettingsRepository settingsRepository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenRevoker revoker,
                               UserRepository userRepository, CompanySettingsRepository settingsRepository,
                               AppProperties props) {
        this.repository = repository;
        this.revoker = revoker;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.ttl = props.security().refresh().ttl();
    }

    /** The raw refresh token (returned once, set as a cookie) plus the persisted record. */
    public record IssuedToken(String rawToken, RefreshToken record) {
    }

    /** Start a brand-new family (called on login). */
    @Transactional
    public IssuedToken issueNewFamily(UUID userId, String userAgent) {
        return issue(userId, UUID.randomUUID(), userAgent);
    }

    private IssuedToken issue(UUID userId, UUID familyId, String userAgent) {
        String raw = TokenGenerator.rawToken();
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(), userId, TokenGenerator.sha256(raw), familyId,
                Instant.now().plus(lifetimeFor(userId)), truncate(userAgent));
        repository.save(token);
        return new IssuedToken(raw, token);
    }

    /**
     * How long this token may live: the configured refresh lifetime, or the company's idle window
     * if that is shorter.
     *
     * <p>This is what makes the idle timeout real rather than a countdown drawn in the browser.
     * The client rotates this token while somebody is using the app, so a token that has not been
     * rotated is a session nobody has touched — and once it passes the idle window it simply does
     * not verify any more. Closing the tab, killing the script, or editing the countdown in dev
     * tools changes nothing: the cookie in hand is already dead.
     *
     * <p>Falls back to the full lifetime whenever the company has no idle policy, which is the
     * default. A missing settings row means the same thing.
     */
    private Duration lifetimeFor(UUID userId) {
        Integer idleMinutes = userRepository.findById(userId)
                .map(user -> settingsRepository.findById(user.getCompanyId())
                        .map(CompanySettings::getSessionIdleMinutes)
                        .orElse(null))
                .orElse(null);
        if (idleMinutes == null) {
            return ttl;
        }
        Duration idle = Duration.ofMinutes(idleMinutes);
        return idle.compareTo(ttl) < 0 ? idle : ttl;
    }

    /**
     * Rotate a presented refresh token. Returns the userId + a freshly issued token.
     * @throws UnauthorizedException if the token is unknown, expired, or reused (family revoked).
     */
    @Transactional
    public RotationResult rotate(String rawToken, String userAgent) {
        RefreshToken existing = repository.findByTokenHash(TokenGenerator.sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));

        Instant now = Instant.now();

        if (existing.isRevoked()) {
            // Reuse of an already-rotated token → likely theft. Burn the whole family in its OWN
            // transaction so the revocation survives the 401 we're about to throw.
            int revoked = revoker.revokeFamily(existing.getFamilyId());
            log.warn("Refresh token reuse detected for family {} — revoked {} tokens",
                    existing.getFamilyId(), revoked);
            throw new UnauthorizedException("Invalid session");
        }
        if (existing.isExpired(now)) {
            throw new UnauthorizedException("Invalid session");
        }

        existing.revoke();                      // one-time use
        repository.save(existing);
        IssuedToken next = issue(existing.getUserId(), existing.getFamilyId(), userAgent);
        return new RotationResult(existing.getUserId(), next);
    }

    /** Revoke the family behind a presented token (logout). Silent if unknown. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(TokenGenerator.sha256(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    public record RotationResult(UUID userId, IssuedToken issued) {
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
