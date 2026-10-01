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

/** A time-off request (People OS slice P4). */
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LeaveType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int days;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaveRequest() {
    }

    public LeaveRequest(UUID id, UUID companyId, UUID employeeId, LeaveType type,
                        LocalDate startDate, LocalDate endDate, int days, String reason) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void decide(LeaveStatus decision, UUID deciderUserId) {
        this.status = decision;
        this.decidedBy = deciderUserId;
        this.decidedAt = Instant.now();
    }

    public void cancel() {
        this.status = LeaveStatus.CANCELLED;
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

    public LeaveType getType() {
        return type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
