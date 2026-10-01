package com.calyvora.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A named performance-review window (e.g. "FY2026 Annual") that an Owner/Admin opens for the company. */
@Entity
@Table(name = "review_cycles")
public class ReviewCycle {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewCycleStatus status = ReviewCycleStatus.OPEN;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewCycle() {
    }

    public ReviewCycle(UUID id, UUID companyId, String name, LocalDate periodStart, LocalDate periodEnd,
                       UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public ReviewCycleStatus getStatus() { return status; }
    public void setStatus(ReviewCycleStatus status) { this.status = status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
