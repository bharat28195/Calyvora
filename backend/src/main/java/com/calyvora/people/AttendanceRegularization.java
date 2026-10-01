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
import java.time.LocalTime;
import java.util.UUID;

/** An employee's request to fix a missed/incorrect attendance day, pending their manager's approval. */
@Entity
@Table(name = "attendance_regularizations")
public class AttendanceRegularization {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Column(name = "check_in")
    private LocalTime checkIn;

    @Column(name = "check_out")
    private LocalTime checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RegularizationStatus status = RegularizationStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendanceRegularization() {
    }

    public AttendanceRegularization(UUID id, UUID companyId, UUID employeeId, LocalDate onDate,
                                    LocalTime checkIn, LocalTime checkOut, String reason) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.onDate = onDate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getOnDate() { return onDate; }
    public LocalTime getCheckIn() { return checkIn; }
    public LocalTime getCheckOut() { return checkOut; }
    public RegularizationStatus getStatus() { return status; }
    public void setStatus(RegularizationStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
