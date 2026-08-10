package com.calyvora.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A tenant. Everything a user does is scoped to their company. */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 140, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CompanyStatus status = CompanyStatus.PENDING;

    /**
     * True for the single vendor company that owns the platform console. Membership of it — not the
     * {@code OWNER} role alone — is what grants access to every tenant's data, so a stray OWNER row
     * can never reach the console (see V35, where self-registration used to create exactly that).
     */
    @Column(name = "is_platform", nullable = false)
    private boolean platform;

    /**
     * True for an agency's own workspace company — the row an {@code AGENCY_OWNER} belongs to. Same
     * pattern as {@link #platform} and for the same reason: the console is granted by membership of
     * this company, not by the role alone (PD-18).
     */
    @Column(name = "is_agency", nullable = false)
    private boolean agency;

    /**
     * The agency workspace this company belongs to, or null for a direct customer. Set once when an
     * agency creates the company; it decides who may see the company's summary.
     */
    @Column(name = "agency_id")
    private UUID agencyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
    }

    public Company(UUID id, String name, String slug, CompanyStatus status) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public boolean isPlatform() {
        return platform;
    }

    public void setPlatform(boolean platform) {
        this.platform = platform;
    }

    public boolean isAgency() {
        return agency;
    }

    public void setAgency(boolean agency) {
        this.agency = agency;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(UUID agencyId) {
        this.agencyId = agencyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
