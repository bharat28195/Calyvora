package com.calyvora.billing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One version of the platform's published price list, valid from {@link #getEffectiveFrom()} until a
 * later version supersedes it.
 *
 * <p>Versioned rather than edited in place so that changing what you charge tomorrow doesn't rewrite
 * what you charged last month. An invoice is priced by the list in force for its own month, which is
 * the only way the billing page stays something a customer can check.
 */
@Entity
@Table(name = "price_lists")
public class PriceList {

    @Id
    private UUID id;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(length = 200)
    private String note;

    /**
     * The floor a company pays regardless of headcount. Protects against the smallest accounts
     * costing more in support than they pay; zero disables it.
     */
    @Column(name = "monthly_minimum", nullable = false)
    private BigDecimal monthlyMinimum = BigDecimal.ZERO;

    /** Months charged for an annual prepayment — 10 means two months free. 12 is no discount. */
    @Column(name = "annual_months_charged", nullable = false)
    private int annualMonthsCharged = 12;

    @OneToMany(mappedBy = "priceList", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("sortOrder asc")
    private List<PriceListTier> tiers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PriceList() {
    }

    public PriceList(UUID id, LocalDate effectiveFrom, String note) {
        this.id = id;
        this.effectiveFrom = effectiveFrom;
        this.note = note;
    }

    public void addTier(PriceListTier tier) {
        tier.setPriceList(this);
        tier.setSortOrder(tiers.size());
        tiers.add(tier);
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getNote() {
        return note;
    }

    public List<PriceListTier> getTiers() {
        return tiers;
    }

    public BigDecimal getMonthlyMinimum() {
        return monthlyMinimum;
    }

    public void setMonthlyMinimum(BigDecimal monthlyMinimum) {
        this.monthlyMinimum = monthlyMinimum == null ? BigDecimal.ZERO : monthlyMinimum;
    }

    public int getAnnualMonthsCharged() {
        return annualMonthsCharged;
    }

    public void setAnnualMonthsCharged(int annualMonthsCharged) {
        this.annualMonthsCharged = annualMonthsCharged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
