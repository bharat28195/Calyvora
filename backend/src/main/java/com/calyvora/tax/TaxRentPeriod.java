package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Rent paid over a run of months at one address (V71). Several of these make a year: a move in
 * October is two rows, with two cities and two landlords — which is exactly why the HRA exemption
 * is worked out month by month rather than from a yearly total.
 */
@Entity
@Table(name = "tax_rent_periods")
public class TaxRentPeriod {

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;
    @Column(name = "from_month", nullable = false, length = 7)
    private String fromMonth;
    @Column(name = "to_month", nullable = false, length = 7)
    private String toMonth;
    @Column(name = "monthly_rent", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyRent;
    @Column(nullable = false, length = 80)
    private String city;
    @Column(nullable = false)
    private boolean metro;
    @Column(name = "landlord_name", length = 160)
    private String landlordName;
    @Column(name = "landlord_pan", length = 10)
    private String landlordPan;
    @Column(name = "landlord_address", length = 300)
    private String landlordAddress;
    @Column(name = "landlord_relationship", length = 60)
    private String landlordRelationship;
    @Column(name = "accepted_rent", precision = 14, scale = 2)
    private BigDecimal acceptedRent;
    @Column(name = "proof_status", nullable = false, length = 16)
    private String proofStatus = ProofStatus.NONE.name();
    @Column(name = "review_note", length = 400)
    private String reviewNote;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected TaxRentPeriod() {
    }

    public TaxRentPeriod(UUID companyId, UUID declarationId) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.declarationId = declarationId;
    }

    public UUID getId() { return id; }
    public UUID getDeclarationId() { return declarationId; }
    public YearMonth getFrom() { return YearMonth.parse(fromMonth); }
    public YearMonth getTo() { return YearMonth.parse(toMonth); }
    public void setMonths(YearMonth from, YearMonth to) { this.fromMonth = from.toString(); this.toMonth = to.toString(); }
    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal v) { this.monthlyRent = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; this.metro = HraCalculator.isMetro(v); }
    public boolean isMetro() { return metro; }
    public String getLandlordName() { return landlordName; }
    public void setLandlordName(String v) { this.landlordName = v; }
    public String getLandlordPan() { return landlordPan; }
    public void setLandlordPan(String v) { this.landlordPan = v; }
    public String getLandlordAddress() { return landlordAddress; }
    public void setLandlordAddress(String v) { this.landlordAddress = v; }
    public String getLandlordRelationship() { return landlordRelationship; }
    public void setLandlordRelationship(String v) { this.landlordRelationship = v; }
    public BigDecimal getAcceptedRent() { return acceptedRent; }
    public ProofStatus getProofStatus() { return ProofStatus.valueOf(proofStatus); }
    public void setProofStatus(ProofStatus s) { this.proofStatus = s.name(); }
    public String getReviewNote() { return reviewNote; }

    public void review(ProofStatus status, BigDecimal acceptedMonthly, String note, UUID by) {
        this.proofStatus = status.name();
        this.acceptedRent = acceptedMonthly;
        this.reviewNote = note;
        this.reviewedBy = by;
        this.reviewedAt = Instant.now();
    }

    /** The monthly rent payroll should use — declared before the proof deadline, accepted after. */
    public BigDecimal effectiveRent(boolean proofsDue) {
        return TaxDeclarationItem.effectiveAmount(proofsDue, monthlyRent, acceptedRent, getProofStatus());
    }

    /** Months covered, both ends included. */
    public int months() {
        return (int) (getFrom().until(getTo(), java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    public boolean covers(YearMonth m) {
        return !m.isBefore(getFrom()) && !m.isAfter(getTo());
    }
}
