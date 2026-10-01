package com.calyvora.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One band of a {@link PriceList}: employees up to {@code upTo} cost {@code rate} each. */
@Entity
@Table(name = "price_list_tiers")
public class PriceListTier {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    /** Cumulative employee count this tier covers; {@code null} on the final, open-ended tier. */
    @Column(name = "up_to")
    private Integer upTo;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PriceListTier() {
    }

    public PriceListTier(UUID id, Integer upTo, BigDecimal rate) {
        this.id = id;
        this.upTo = upTo;
        this.rate = rate;
    }

    public UUID getId() {
        return id;
    }

    public Integer getUpTo() {
        return upTo;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    void setPriceList(PriceList priceList) {
        this.priceList = priceList;
    }
}
