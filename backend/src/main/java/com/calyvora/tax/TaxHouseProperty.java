package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A house the employee owns and pays a loan on (V71). Self-occupied, its interest is a loss of up to
 * ₹2,00,000 in the old regime; let out, its rent is income and the interest comes off that.
 */
@Entity
@Table(name = "tax_house_properties")
public class TaxHouseProperty {

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;
    @Column(name = "let_out", nullable = false)
    private boolean letOut;
    @Column(length = 300)
    private String address;
    @Column(name = "lender_name", length = 160)
    private String lenderName;
    @Column(name = "lender_pan", length = 10)
    private String lenderPan;
    @Column(name = "lender_address", length = 300)
    private String lenderAddress;
    /** Form 124 item 3(iv): FINANCIAL_INSTITUTION, EMPLOYER or OTHER (V72). */
    @Column(name = "lender_type", length = 24)
    private String lenderType;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal interest = BigDecimal.ZERO;
    @Column(name = "annual_rent", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualRent = BigDecimal.ZERO;
    @Column(name = "municipal_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal municipalTax = BigDecimal.ZERO;
    @Column(name = "accepted_interest", precision = 14, scale = 2)
    private BigDecimal acceptedInterest;
    @Column(name = "proof_status", nullable = false, length = 16)
    private String proofStatus = ProofStatus.NONE.name();
    @Column(name = "review_note", length = 400)
    private String reviewNote;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected TaxHouseProperty() {
    }

    public TaxHouseProperty(UUID companyId, UUID declarationId) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.declarationId = declarationId;
    }

    public UUID getId() { return id; }
    public UUID getDeclarationId() { return declarationId; }
    public boolean isLetOut() { return letOut; }
    public void setLetOut(boolean v) { this.letOut = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getLenderName() { return lenderName; }
    public void setLenderName(String v) { this.lenderName = v; }
    public String getLenderPan() { return lenderPan; }
    public void setLenderPan(String v) { this.lenderPan = v; }
    public String getLenderAddress() { return lenderAddress; }
    public void setLenderAddress(String v) { this.lenderAddress = v; }
    public String getLenderType() { return lenderType; }
    public void setLenderType(String v) { this.lenderType = v; }
    public BigDecimal getInterest() { return interest; }
    public void setInterest(BigDecimal v) { this.interest = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getAnnualRent() { return annualRent; }
    public void setAnnualRent(BigDecimal v) { this.annualRent = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getMunicipalTax() { return municipalTax; }
    public void setMunicipalTax(BigDecimal v) { this.municipalTax = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getAcceptedInterest() { return acceptedInterest; }
    public ProofStatus getProofStatus() { return ProofStatus.valueOf(proofStatus); }
    public void setProofStatus(ProofStatus s) { this.proofStatus = s.name(); }
    public String getReviewNote() { return reviewNote; }

    public void review(ProofStatus status, BigDecimal accepted, String note, UUID by) {
        this.proofStatus = status.name();
        this.acceptedInterest = accepted;
        this.reviewNote = note;
        this.reviewedBy = by;
        this.reviewedAt = Instant.now();
    }

    /**
     * The interest payroll should use. Rent from a let-out house is income, so it always counts —
     * only the interest, which lowers the tax, waits on a proof.
     */
    public BigDecimal effectiveInterest(boolean proofsDue) {
        return TaxDeclarationItem.effectiveAmount(proofsDue, interest, acceptedInterest, getProofStatus());
    }
}
