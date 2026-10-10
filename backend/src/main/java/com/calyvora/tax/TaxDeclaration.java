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

    /** Either parent 60 or older — raises their Section 126 ceiling to ₹50,000 (V71). */
    @Column(name = "parents_senior", nullable = false)
    private boolean parentsSenior;

    // Income from an earlier employer this year — what their Form 130 (Form 16) shows (V71).
    @Column(name = "prev_employer_name", length = 160)
    private String prevEmployerName;
    @Column(name = "prev_employer_tan", length = 10)
    private String prevEmployerTan;
    @Column(name = "prev_income", precision = 14, scale = 2)
    private java.math.BigDecimal prevIncome;
    @Column(name = "prev_tds", precision = 14, scale = 2)
    private java.math.BigDecimal prevTds;
    @Column(name = "prev_pf", precision = 14, scale = 2)
    private java.math.BigDecimal prevPf;
    @Column(name = "prev_pt", precision = 14, scale = 2)
    private java.math.BigDecimal prevPt;
    /** NONE, SUBMITTED, ACCEPTED or REJECTED — a rejected block is left out of the computation. */
    @Column(name = "prev_status", nullable = false, length = 16)
    private String prevStatus = "NONE";
    @Column(name = "prev_review_note", length = 400)
    private String prevReviewNote;

    /** Form 124 item 1 — the address the employee certified this year (V72). */
    @Column(name = "employee_address", length = 400)
    private String employeeAddress;

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

    public boolean isParentsSenior() { return parentsSenior; }
    public void setParentsSenior(boolean v) { this.parentsSenior = v; }
    public String getPrevEmployerName() { return prevEmployerName; }
    public void setPrevEmployerName(String v) { this.prevEmployerName = v; }
    public String getPrevEmployerTan() { return prevEmployerTan; }
    public void setPrevEmployerTan(String v) { this.prevEmployerTan = v; }
    public java.math.BigDecimal getPrevIncome() { return prevIncome; }
    public void setPrevIncome(java.math.BigDecimal v) { this.prevIncome = v; }
    public java.math.BigDecimal getPrevTds() { return prevTds; }
    public void setPrevTds(java.math.BigDecimal v) { this.prevTds = v; }
    public java.math.BigDecimal getPrevPf() { return prevPf; }
    public void setPrevPf(java.math.BigDecimal v) { this.prevPf = v; }
    public java.math.BigDecimal getPrevPt() { return prevPt; }
    public void setPrevPt(java.math.BigDecimal v) { this.prevPt = v; }
    public String getPrevStatus() { return prevStatus; }
    public void setPrevStatus(String v) { this.prevStatus = v; }
    public String getPrevReviewNote() { return prevReviewNote; }
    public void setPrevReviewNote(String v) { this.prevReviewNote = v; }
    public String getEmployeeAddress() { return employeeAddress; }
    public void setEmployeeAddress(String v) { this.employeeAddress = v; }

    /** Whether there is an earlier employer's income on this declaration that counts. */
    public boolean hasPreviousEmployer() {
        return !"REJECTED".equals(prevStatus)
                && ((prevIncome != null && prevIncome.signum() > 0) || (prevTds != null && prevTds.signum() > 0));
    }

    /** Back to editable — what HR does when it reopens the window for somebody. */
    public void reopen() {
        this.status = Status.DRAFT;
        this.submittedAt = null;
    }
}
