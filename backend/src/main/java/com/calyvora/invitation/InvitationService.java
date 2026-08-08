package com.calyvora.invitation;

import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.error.TokenExpiredException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.common.config.AppProperties;
import com.calyvora.common.util.TokenGenerator;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.email.EmailService;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.invitation.dto.AcceptInvitationRequest;
import com.calyvora.invitation.dto.CreateInvitationRequest;
import com.calyvora.invitation.dto.InvitationPreviewResponse;
import com.calyvora.invitation.dto.InvitationResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invitation lifecycle (F8/F9): create, list, revoke (all tenant-scoped, OWNER/ADMIN), plus the
 * public preview + accept flow that creates a new active member.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final com.calyvora.billing.SubscriptionRepository subscriptionRepository;

    public InvitationService(InvitationRepository invitationRepository,
                             UserRepository userRepository,
                             CompanyRepository companyRepository,
                             EmailService emailService,
                             PasswordEncoder passwordEncoder,
                             AppProperties props,
                             com.calyvora.billing.SubscriptionRepository subscriptionRepository) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Refuse to hand out a seat the company is not paying for (PD-10 pt 3). Seats are consumed by
     * active members <em>and</em> outstanding invitations — counting only members would let an admin
     * issue a hundred invitations against five seats and blow the limit at accept time, long after
     * the point where a helpful error was possible.
     *
     * <p>Companies with no subscription row (the platform company itself) are unlimited.
     */
    private void requireFreeSeat(UUID companyId) {
        subscriptionRepository.findByCompanyId(companyId).ifPresent(sub -> {
            long used = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE)
                    + invitationRepository.countByCompanyIdAndStatus(companyId, InvitationStatus.PENDING);
            if (used >= sub.getSeats()) {
                throw new ApiException(ErrorCode.SEAT_LIMIT_REACHED,
                        "All " + sub.getSeats() + " seats are in use. Ask for more seats before inviting anyone else.");
            }
        });
    }

    @Transactional
    public InvitationResponse create(CreateInvitationRequest request, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        String email = request.email().trim().toLowerCase();
        Role role = Role.valueOf(request.role());

        if (userRepository.existsByCompanyIdAndEmail(companyId, email)) {
            throw new ConflictException("That person is already a member");
        }
        invitationRepository.findByCompanyIdAndEmailAndStatus(companyId, email, InvitationStatus.PENDING)
                .ifPresent(i -> {
                    throw new ConflictException("An invitation is already pending for that email");
                });
        requireFreeSeat(companyId);

        String rawToken = TokenGenerator.rawToken();
        Invitation invitation = new Invitation(UUID.randomUUID(), companyId, email, role,
                TokenGenerator.sha256(rawToken), principal.userId(),
                Instant.now().plus(props.security().invitation().ttl()));
        invitationRepository.save(invitation);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
        String link = acceptUrl(rawToken);
        emailService.sendInvitationEmail(email, company.getName(), link);

        // The link goes back to the admin as well as into the email. Onboarding a colleague must not
        // depend on mail being deliverable — the admin can pass it on however they like.
        return InvitationResponse.of(invitation, principal.email(), link);
    }

    /**
     * Issue a fresh joining link for a pending invitation.
     *
     * <p>Needed because the token is stored hashed: once the original link is lost there is no way to
     * read it back, only to replace it. Regenerating invalidates the previous link, which is also the
     * right behaviour if it was sent to the wrong place.
     */
    @Transactional
    public InvitationResponse regenerateLink(UUID invitationId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Invitation invitation = invitationRepository.findById(invitationId)
                .filter(i -> i.getCompanyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("That invitation is no longer pending");
        }

        String rawToken = TokenGenerator.rawToken();
        invitation.setTokenHash(TokenGenerator.sha256(rawToken));
        invitation.setExpiresAt(Instant.now().plus(props.security().invitation().ttl()));

        return InvitationResponse.of(invitation, emailOf(invitation.getInvitedBy()), acceptUrl(rawToken));
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPending() {
        UUID companyId = TenantContext.getCompanyId();
        List<Invitation> invitations = invitationRepository
                .findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, InvitationStatus.PENDING);

        Map<UUID, String> inviterEmails = new HashMap<>();
        return invitations.stream()
                .map(inv -> InvitationResponse.of(inv,
                        inviterEmails.computeIfAbsent(inv.getInvitedBy(), this::emailOf)))
                .toList();
    }

    @Transactional
    public void revoke(UUID invitationId) {
        UUID companyId = TenantContext.getCompanyId();
        Invitation invitation = invitationRepository.findByIdAndCompanyId(invitationId, companyId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        if (invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.revoke();
        }
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String rawToken) {
        Invitation invitation = validPending(rawToken);
        Company company = companyRepository.findById(invitation.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        return new InvitationPreviewResponse(invitation.getEmail(), company.getName(),
                invitation.getRole().name());
    }

    @Transactional
    public void accept(AcceptInvitationRequest request) {
        Invitation invitation = validPending(request.token());

        if (userRepository.existsByEmail(invitation.getEmail())) {
            // Email is globally unique (SD-3); the address was registered elsewhere in the meantime.
            throw new ConflictException("That email is already registered");
        }
        // Checked again here, not just at invite time: seats can be cut, or the subscription can
        // lapse, between issuing a link and someone clicking it.
        requireActiveSubscription(invitation.getCompanyId());

        User user = new User(UUID.randomUUID(), invitation.getCompanyId(), invitation.getEmail(),
                request.firstName().trim(), request.lastName().trim(), invitation.getRole(),
                UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        invitation.accept();
    }

    // ---- helpers ----

    /**
     * Joining a workspace whose subscription has ended must fail. The accept endpoint is public, so
     * it runs with no tenant bound and {@code SubscriptionLockFilter} cannot cover it — the company
     * comes from the invitation instead.
     *
     * <p>No seat check here on purpose: the pending invitation already reserved the seat in
     * {@link #requireFreeSeat}, and accepting only converts that reservation into a member.
     */
    private void requireActiveSubscription(UUID companyId) {
        subscriptionRepository.findByCompanyId(companyId)
                .filter(com.calyvora.billing.Subscription::isLocked)
                .ifPresent(sub -> {
                    throw new ApiException(ErrorCode.SUBSCRIPTION_INACTIVE,
                            "This workspace is locked because its subscription has ended.");
                });
    }

    private Invitation validPending(String rawToken) {
        Invitation invitation = invitationRepository.findByTokenHash(TokenGenerator.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "This invitation link is invalid"));
        if (invitation.getStatus() == InvitationStatus.REVOKED
                || invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new TokenExpiredException("This invitation is no longer valid");
        }
        if (invitation.isExpired(Instant.now())) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.markExpired();
            }
            throw new TokenExpiredException("This invitation has expired");
        }
        return invitation;
    }

    private String emailOf(UUID userId) {
        return userRepository.findById(userId).map(User::getEmail).orElse("unknown");
    }

    private String acceptUrl(String rawToken) {
        return props.frontendBaseUrl() + "/accept-invite?token=" + rawToken;
    }
}
