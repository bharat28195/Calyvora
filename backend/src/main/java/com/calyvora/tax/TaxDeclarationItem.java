package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One deduction claimed on a declaration, at the amount the employee entered.
 *
 * <p>Stored uncapped on purpose. The statutory ceiling is applied when the tax is computed, so that
 * the screen can say "you claimed ₹5,00,000, ₹1,50,000 is allowable" instead of silently trimming
 * the figure and leaving the employee to wonder where the rest went — and so that a limit raised by
 * a future Finance Act reprices declarations already on file without anybody re-entering them.
 */
@Entity
@Table(name = "tax_declaration_items")
public class TaxDeclarationItem {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TaxDeduction deduction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** What HR accepted against the proofs; null until reviewed (V71). */
    @Column(name = "accepted_amount", precision = 14, scale = 2)
    private BigDecimal acceptedAmount;

    @Column(name = "proof_status", nullable = false, length = 16)
    private String proofStatus = ProofStatus.NONE.name();

    @Column(name = "review_note", length = 400)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.Instant reviewedAt;

    /** A policy number, an institution — whatever helps HR match the proof. */
    @Column(length = 300)
    private String detail;

    protected TaxDeclarationItem() {
    }

    public TaxDeclarationItem(UUID companyId, UUID declarationId, TaxDeduction deduction, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.declarationId = declarationId;
        this.deduction = deduction;
        setAmount(amount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public TaxDeduction getDeduction() {
        return deduction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getCompanyId() { return companyId; }
    public BigDecimal getAcceptedAmount() { return acceptedAmount; }
    public ProofStatus getProofStatus() { return ProofStatus.valueOf(proofStatus); }
    public void setProofStatus(ProofStatus s) { this.proofStatus = s.name(); }
    public String getReviewNote() { return reviewNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public java.time.Instant getReviewedAt() { return reviewedAt; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    /** HR's decision on the proof: the amount it stands behind, and why. */
    public void review(ProofStatus status, BigDecimal accepted, String note, UUID by) {
        this.proofStatus = status.name();
        this.acceptedAmount = accepted;
        this.reviewNote = note;
        this.reviewedBy = by;
        this.reviewedAt = java.time.Instant.now();
    }

    /**
     * The amount payroll should use. Before the proof deadline that is what was declared; after it,
     * only what HR accepted — an unproved claim stops reducing the tax.
     */
    public BigDecimal effective(boolean proofsDue) {
        return effectiveAmount(proofsDue, amount, acceptedAmount, getProofStatus());
    }

    static BigDecimal effectiveAmount(boolean proofsDue, BigDecimal declared, BigDecimal accepted, ProofStatus status) {
        if (!proofsDue) {
            return declared;
        }
        return switch (status) {
            case ACCEPTED, PARTIAL -> accepted == null ? BigDecimal.ZERO : accepted.min(declared);
            default -> BigDecimal.ZERO;
        };
    }

    /** Negative claims are not a thing; they would increase somebody's taxable income. */
    public void setAmount(BigDecimal amount) {
        this.amount = amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }
}
