package com.calyvora.recruit;

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

/** An applicant for a {@link JobOpening}, moving through the hiring pipeline. */
@Entity
@Table(name = "candidates")
public class Candidate {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    @Column(length = 60)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CandidateStage stage = CandidateStage.APPLIED;

    @Column
    private Integer rating;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Candidate() {
    }

    public Candidate(UUID id, UUID companyId, UUID jobId, String name, String email, String phone,
                     String resumeUrl, String source) {
        this.id = id;
        this.companyId = companyId;
        this.jobId = jobId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.resumeUrl = resumeUrl;
        this.source = source;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getJobId() { return jobId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public CandidateStage getStage() { return stage; }
    public void setStage(CandidateStage stage) { this.stage = stage; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
