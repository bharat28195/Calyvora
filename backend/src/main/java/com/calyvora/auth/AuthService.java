package com.calyvora.auth;

import com.calyvora.auth.dto.LoginResponse;
import com.calyvora.auth.dto.MeResponse;
import com.calyvora.auth.dto.RegisterRequest;
import com.calyvora.common.config.AppProperties;
import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.error.TokenExpiredException;
import com.calyvora.common.error.UnauthorizedException;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.JwtService;
import com.calyvora.common.util.Slugs;
import com.calyvora.common.util.TokenGenerator;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.company.CompanyStatus;
import com.calyvora.email.EmailService;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registration, email verification, and login/session orchestration (F2, F3, F6). Tenant creation
 * happens here: register creates the Company + Owner atomically (SD-4); verification activates both.
 */
@Service
public class AuthService {

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AppProperties props;

    public AuthService(CompanyRepository companyRepository,
                       CompanySettingsRepository companySettingsRepository,
                       UserRepository userRepository,
                       EmailVerificationTokenRepository verificationTokenRepository,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       AppProperties props) {
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.props = props;
    }

    // ---- F2: registration -------------------------------------------------

    /**
     * @return whether the verification email actually reached the provider. The account is created
     *         either way — but the caller must not tell the user to "check your email" for a message
     *         that was never delivered, which is exactly what a blocked SMTP port produces.
     */
    @Transactional
    public boolean register(RegisterRequest request) {
        // The door is bolted by default (PD-21). Open self-signup created a live company and an ADMIN
        // who could sign in that second — so "start free trial" on the website was, in effect, a
        // handout to anyone who found the URL. Trials are now requested and granted by a person; see
        // TrialRequestService. The endpoint stays (and stays public) so this refusal is an explicit,
        // explainable 403 rather than a 404 that reads like a broken site.
        if (!props.security().registration().open()) {
            throw new ForbiddenException(
                    "Orbit accounts are set up by us. Request a free trial and we'll be in touch — "
                            + "you'll get your sign-in details once it's approved.");
        }
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("That email is already registered");
        }

        // Signing up creates a company and its first ADMIN. Deliberately not OWNER: that role is the
        // platform vendor above every tenant, and granting it here let anyone who registered read
        // every customer's data from the owner console (fixed in V35).
        boolean verificationRequired = props.security().verification().required();

        Company company = new Company(UUID.randomUUID(), request.companyName().trim(),
                uniqueSlug(request.companyName()),
                verificationRequired ? CompanyStatus.PENDING : CompanyStatus.ACTIVE);
        companyRepository.save(company);
        companySettingsRepository.save(new CompanySettings(company.getId()));

        User admin = new User(UUID.randomUUID(), company.getId(), email,
                request.firstName().trim(), request.lastName().trim(), Role.ADMIN,
                verificationRequired ? UserStatus.PENDING_VERIFICATION : UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        if (!verificationRequired) {
            admin.setEmailVerifiedAt(Instant.now());
        }
        userRepository.save(admin);

        // The email still goes out when a mailbox is configured — it's a useful welcome and confirms
        // the address — but with verification off it never stands between someone and their workspace.
        String rawToken = issueVerificationToken(admin.getId());
        return emailService.sendVerificationEmail(email, verificationUrl(rawToken)).delivered();
    }

    // ---- F3: verification -------------------------------------------------

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = verificationTokenRepository
                .findByTokenHash(TokenGenerator.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "This verification link is invalid"));

        Instant now = Instant.now();
        if (token.isConsumed()) {
            throw new TokenExpiredException("This link has already been used");
        }
        if (token.isExpired(now)) {
            throw new TokenExpiredException("This link has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);

        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        if (company.getStatus() == CompanyStatus.PENDING) {
            company.setStatus(CompanyStatus.ACTIVE);
        }

        token.consume();
    }

    @Transactional
    public void resendVerification(String email) {
        // No enumeration: always succeed silently; only actually send if applicable.
        userRepository.findByEmail(normalize(email))
                .filter(u -> u.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(u -> {
                    String rawToken = issueVerificationToken(u.getId());
                    emailService.sendVerificationEmail(u.getEmail(), verificationUrl(rawToken));
                });
    }

    // ---- F6: login / session ---------------------------------------------

    @Transactional
    public LoginResult login(String email, String password, String userAgent) {
        User user = userRepository.findByEmail(normalize(email)).orElse(null);
        // Generic failure for both unknown email and bad password (no enumeration).
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        // Only blocks when verification is switched on. Off (the default), an account created before
        // it was disabled still logs in — otherwise every signup made while mail was broken would
        // stay permanently locked out with no way to rescue it.
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION
                && props.security().verification().required()) {
            throw new ForbiddenException("Please verify your email before logging in");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Your account is not active");
        }
        return issueSession(user, refreshTokenService.issueNewFamily(user.getId(), userAgent));
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Invalid session");
        }
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken, userAgent);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid session");
        }
        return issueSession(user, rotation.issued());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        CompanySettings settings = companySettingsRepository.findById(user.getCompanyId()).orElse(null);
        return MeResponse.of(user, company, settings);
    }

    /** Login/refresh result: the access token, the raw refresh token (→ cookie), and the body. */
    public record LoginResult(String accessToken, String refreshToken, LoginResponse body) {
    }

    // ---- helpers ----------------------------------------------------------

    private LoginResult issueSession(User user, RefreshTokenService.IssuedToken issued) {
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getCompanyId(),
                user.getRole().name(), user.getEmail());
        String accessToken = jwtService.createAccessToken(principal);
        CompanySettings settings = companySettingsRepository.findById(user.getCompanyId()).orElse(null);
        LoginResponse body = new LoginResponse(accessToken, MeResponse.of(user, company, settings));
        return new LoginResult(accessToken, issued.rawToken(), body);
    }

    private String issueVerificationToken(UUID userId) {
        String raw = TokenGenerator.rawToken();
        verificationTokenRepository.save(new EmailVerificationToken(
                UUID.randomUUID(), userId, TokenGenerator.sha256(raw),
                Instant.now().plus(props.security().verification().ttl())));
        return raw;
    }

    private String verificationUrl(String rawToken) {
        return props.frontendBaseUrl() + "/verify-email?token=" + rawToken;
    }

    private String uniqueSlug(String companyName) {
        String base = Slugs.slugify(companyName);
        String slug = base;
        int suffix = 1;
        while (companyRepository.existsBySlug(slug)) {
            slug = base + "-" + (++suffix);
        }
        return slug;
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** Exposed for a couple of tests / callers that only need active members. */
    List<User> activeMembers(UUID companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }
}
