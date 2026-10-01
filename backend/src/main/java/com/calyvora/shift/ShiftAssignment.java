package com.calyvora.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One employee rostered onto one shift on one day. At most one per employee per day. */
@Entity
@Table(name = "shift_assignments")
public class ShiftAssignment {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShiftAssignment() {
    }

    public ShiftAssignment(UUID id, UUID companyId, UUID employeeId, UUID shiftId, LocalDate onDate) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.shiftId = shiftId;
        this.onDate = onDate;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getShiftId() { return shiftId; }
    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }
    public LocalDate getOnDate() { return onDate; }
    public Instant getCreatedAt() { return createdAt; }
}
