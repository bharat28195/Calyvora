package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A point-in-time salary for an employee (People OS). The latest by effective date is current pay. */
@Entity
@Table(name = "compensation_records")
public class CompensationRecord {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "annual_amount", nullable = false)
    private BigDecimal annualAmount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 24)
    private CompensationChangeType changeType = CompensationChangeType.ADJUSTMENT;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompensationRecord() {
    }

    public CompensationRecord(UUID id, UUID companyId, UUID employeeId, LocalDate effectiveDate,
                              BigDecimal annualAmount, String currency, CompensationChangeType changeType,
                              String reason, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.effectiveDate = effectiveDate;
        this.annualAmount = annualAmount;
        this.currency = currency;
        this.changeType = changeType;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public BigDecimal getAnnualAmount() { return annualAmount; }
    public String getCurrency() { return currency; }
    public CompensationChangeType getChangeType() { return changeType; }
    public String getReason() { return reason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
