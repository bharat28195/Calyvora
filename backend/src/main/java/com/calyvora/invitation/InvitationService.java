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

    public InvitationService(InvitationRepository invitationRepository,
                             UserRepository userRepository,
                             CompanyRepository companyRepository,
                             EmailService emailService,
                             PasswordEncoder passwordEncoder,
                             AppProperties props) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
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

        String rawToken = TokenGenerator.rawToken();
        Invitation invitation = new Invitation(UUID.randomUUID(), companyId, email, role,
                TokenGenerator.sha256(rawToken), principal.userId(),
                Instant.now().plus(props.security().invitation().ttl()));
        invitationRepository.save(invitation);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
        emailService.sendInvitationEmail(email, company.getName(), acceptUrl(rawToken));

        return InvitationResponse.of(invitation, principal.email());
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

        User user = new User(UUID.randomUUID(), invitation.getCompanyId(), invitation.getEmail(),
                request.firstName().trim(), request.lastName().trim(), invitation.getRole(),
                UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        invitation.accept();
    }

    // ---- helpers ----

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
