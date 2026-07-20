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

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("That email is already registered");
        }

        Company company = new Company(UUID.randomUUID(), request.companyName().trim(),
                uniqueSlug(request.companyName()), CompanyStatus.PENDING);
        companyRepository.save(company);
        companySettingsRepository.save(new CompanySettings(company.getId()));

        User owner = new User(UUID.randomUUID(), company.getId(), email,
                request.firstName().trim(), request.lastName().trim(), Role.OWNER,
                UserStatus.PENDING_VERIFICATION);
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(owner);

        String rawToken = issueVerificationToken(owner.getId());
        emailService.sendVerificationEmail(email, verificationUrl(rawToken));
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
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
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
        return MeResponse.of(user, company);
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
        LoginResponse body = new LoginResponse(accessToken, MeResponse.of(user, company));
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
