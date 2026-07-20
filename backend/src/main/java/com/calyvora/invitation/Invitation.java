package com.calyvora.invitation;

import com.calyvora.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A pending invitation for someone to join a company with a given role (ADMIN or MEMBER). */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Role role;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invitation() {
    }

    public Invitation(UUID id, UUID companyId, String email, Role role, String tokenHash,
                      UUID invitedBy, Instant expiresAt) {
        this.id = id;
        this.companyId = companyId;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        // Set at construction (not @PrePersist): Spring Data save() merges assigned-id entities, so
        // reading createdAt on the passed instance right after save() would otherwise be null.
        this.createdAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    public void revoke() {
        this.status = InvitationStatus.REVOKED;
    }

    public void markExpired() {
        this.status = InvitationStatus.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
