package com.calyvora.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee's review within a cycle. The self-assessment side is owned by the member; the rating,
 * summary and hike recommendation are owned by the manager; approval and the applied raise are the
 * admin's. Kept as one row so the whole conversation — what they said, what the manager said, what
 * was decided — lives in one place come hike time.
 */
@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReviewStatus status = ReviewStatus.PENDING_SELF;

    @Column(name = "self_assessment", columnDefinition = "text")
    private String selfAssessment;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_summary", columnDefinition = "text")
    private String managerSummary;

    @Column(columnDefinition = "text")
    private String strengths;

    @Column(columnDefinition = "text")
    private String improvements;

    @Enumerated(EnumType.STRING)
    @Column(name = "hike_type", length = 16)
    private HikeType hikeType;

    @Column(name = "hike_percent")
    private BigDecimal hikePercent;

    @Column(name = "proposed_salary")
    private BigDecimal proposedSalary;

    @Column(name = "hike_note", length = 500)
    private String hikeNote;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "applied_comp_id")
    private UUID appliedCompId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PerformanceReview() {
    }

    public PerformanceReview(UUID id, UUID companyId, UUID cycleId, UUID employeeId, UUID managerId) {
        this.id = id;
        this.companyId = companyId;
        this.cycleId = cycleId;
        this.employeeId = employeeId;
        this.managerId = managerId;
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
    public UUID getCycleId() { return cycleId; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getManagerId() { return managerId; }
    public void setManagerId(UUID managerId) { this.managerId = managerId; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getSelfAssessment() { return selfAssessment; }
    public void setSelfAssessment(String selfAssessment) { this.selfAssessment = selfAssessment; }
    public Instant getSelfSubmittedAt() { return selfSubmittedAt; }
    public void setSelfSubmittedAt(Instant selfSubmittedAt) { this.selfSubmittedAt = selfSubmittedAt; }
    public Integer getManagerRating() { return managerRating; }
    public void setManagerRating(Integer managerRating) { this.managerRating = managerRating; }
    public String getManagerSummary() { return managerSummary; }
    public void setManagerSummary(String managerSummary) { this.managerSummary = managerSummary; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public HikeType getHikeType() { return hikeType; }
    public void setHikeType(HikeType hikeType) { this.hikeType = hikeType; }
    public BigDecimal getHikePercent() { return hikePercent; }
    public void setHikePercent(BigDecimal hikePercent) { this.hikePercent = hikePercent; }
    public BigDecimal getProposedSalary() { return proposedSalary; }
    public void setProposedSalary(BigDecimal proposedSalary) { this.proposedSalary = proposedSalary; }
    public String getHikeNote() { return hikeNote; }
    public void setHikeNote(String hikeNote) { this.hikeNote = hikeNote; }
    public Instant getManagerSubmittedAt() { return managerSubmittedAt; }
    public void setManagerSubmittedAt(Instant managerSubmittedAt) { this.managerSubmittedAt = managerSubmittedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public UUID getAppliedCompId() { return appliedCompId; }
    public void setAppliedCompId(UUID appliedCompId) { this.appliedCompId = appliedCompId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
