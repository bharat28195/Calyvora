package com.calyvora.expense;

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
import java.time.LocalDate;
import java.util.UUID;

/** One expense claim. Anemic; the rules live in {@link ExpenseService}. */
@Entity
@Table(name = "expense_claims")
public class ExpenseClaim {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExpenseCategory category = ExpenseCategory.OTHER;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(length = 1000)
    private String description;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExpenseStatus status = ExpenseStatus.SUBMITTED;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 400)
    private String decisionNote;

    @Column(name = "reimbursed_at")
    private Instant reimbursedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExpenseClaim() {
    }

    public ExpenseClaim(UUID id, UUID companyId, UUID employeeId, String title, ExpenseCategory category,
                        BigDecimal amount, String currency, LocalDate spentOn) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.title = title;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.spentOn = spentOn;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Records a decision. Reimbursement is a separate step (see {@link #reimburse}). */
    public void decide(ExpenseStatus decision, UUID by, String note) {
        this.status = decision;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }

    public void reimburse() {
        this.status = ExpenseStatus.REIMBURSED;
        this.reimbursedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public LocalDate getSpentOn() { return spentOn; }
    public void setSpentOn(LocalDate spentOn) { this.spentOn = spentOn; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }
    public ExpenseStatus getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getReimbursedAt() { return reimbursedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
