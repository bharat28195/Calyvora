package com.calyvora.tax;

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

/**
 * One employee's tax declaration for one financial year: which regime, and what they are claiming.
 *
 * <p>Per employee and per year because both halves change. The regime is an individual's choice —
 * which one is cheaper depends entirely on how much they have to deduct — and it can be revisited
 * each April, which is why the year is part of the identity rather than a field that gets
 * overwritten.
 */
@Entity
@Table(name = "tax_declarations")
public class TaxDeclaration {

    /** Where a declaration is in its life: being edited, or declared and relied upon by payroll. */
    public enum Status {
        DRAFT,
        SUBMITTED
    }

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** The label people use — "2026-27" — because it is what every form and payslip shows. */
    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TaxRegime regime = TaxRegime.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TaxDeclaration() {
    }

    public TaxDeclaration(UUID companyId, UUID employeeId, String financialYear) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.financialYear = financialYear;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
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

    public String getFinancialYear() {
        return financialYear;
    }

    public TaxRegime getRegime() {
        return regime;
    }

    public void setRegime(TaxRegime regime) {
        this.regime = regime == null ? TaxRegime.DEFAULT : regime;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Declare it. Payroll may rely on a submitted declaration; a draft is still being thought about. */
    public void submit() {
        this.status = Status.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    /** Back to editable — what HR does when it reopens the window for somebody. */
    public void reopen() {
        this.status = Status.DRAFT;
        this.submittedAt = null;
    }
}
