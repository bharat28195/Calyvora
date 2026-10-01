package com.calyvora.trial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone asking for a trial (PD-21). Not an account — nothing here can be logged into. The platform
 * owner reads these in the console and decides; approval provisions the company through the ordinary
 * vendor route, which is the only way a customer workspace is ever created.
 */
@Entity
@Table(name = "trial_requests")
public class TrialRequest {

    @Id
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "team_size", length = 40)
    private String teamSize;

    @Column(length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrialRequestStatus status = TrialRequestStatus.NEW;

    @Column(length = 80)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "company_id")
    private UUID companyId;

    protected TrialRequest() {
    }

    public TrialRequest(UUID id, String companyName, String contactName, String email,
                        String phone, String teamSize, String note, String source) {
        this.id = id;
        this.companyName = companyName;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.teamSize = teamSize;
        this.note = note;
        this.source = source;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Record the decision and when it was taken, so the queue can show how long someone waited. */
    public void decide(TrialRequestStatus decision, UUID companyId) {
        this.status = decision;
        this.companyId = companyId;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getTeamSize() { return teamSize; }
    public String getNote() { return note; }
    public TrialRequestStatus getStatus() { return status; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public UUID getCompanyId() { return companyId; }
}
