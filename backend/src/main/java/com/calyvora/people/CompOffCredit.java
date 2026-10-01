package com.calyvora.people;

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

/**
 * One day worked that was not owed, earning one day off later.
 *
 * <p>Credits rather than a running total, so each one can be traced: worked on this date, approved
 * by this person, expires then, spent on that leave request. A single counter would make "why do I
 * have three days?" unanswerable and expiry impossible to implement.
 */
@Entity
@Table(name = "comp_off_credits")
public class CompOffCredit {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "worked_on", nullable = false)
    private LocalDate workedOn;

    @Column(length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompOffStatus status = CompOffStatus.PENDING;

    /** Set at approval from the company's policy. Null while pending — nothing expires unearned. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The leave request that spent this credit. Null until used; this is what makes it single-use. */
    @Column(name = "consumed_by")
    private UUID consumedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompOffCredit() {
    }

    public CompOffCredit(UUID id, UUID companyId, UUID employeeId, LocalDate workedOn, String reason) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.workedOn = workedOn;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void approve(UUID by, LocalDate expiresOn) {
        this.status = CompOffStatus.APPROVED;
        this.expiresOn = expiresOn;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID by) {
        this.status = CompOffStatus.REJECTED;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public void consume(UUID leaveRequestId) {
        this.status = CompOffStatus.CONSUMED;
        this.consumedBy = leaveRequestId;
    }

    /**
     * Whether this credit can still be spent on {@code asOf}.
     *
     * <p>Expiry is computed rather than stored as a status, so a credit never needs a scheduled job
     * to expire it — and a policy change that lengthens the window does not have to resurrect rows
     * some batch already marked dead.
     */
    public boolean isSpendable(LocalDate asOf) {
        return status == CompOffStatus.APPROVED && (expiresOn == null || !asOf.isAfter(expiresOn));
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWorkedOn() {
        return workedOn;
    }

    public String getReason() {
        return reason;
    }

    public CompOffStatus getStatus() {
        return status;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getConsumedBy() {
        return consumedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
