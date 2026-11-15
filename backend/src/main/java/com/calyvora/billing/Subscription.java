package com.calyvora.billing;

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
import java.util.UUID;

/**
 * A company's subscription to the HR platform. Billing is <em>per active employee, per month</em>:
 * the monthly charge is {@code pricePerEmployee × active headcount}, so a company that grows from 5 to
 * 20 people is billed for 20 the month it has 20. One row per company.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    /** The pricing MODEL (PER_EMPLOYEE). Not the feature package — that lives on Company.planCode. */
    @Column(nullable = false, length = 40)
    private String plan = "PER_EMPLOYEE";

    @Column(name = "price_per_employee", nullable = false)
    private BigDecimal pricePerEmployee;

    /**
     * False (the default) means bill from the published price list ({@link PricingService}). True means
     * platform owner quoted this company its own flat rate, which then applies to every employee and
     * must not be silently overwritten by a price-list change.
     */
    @Column(name = "custom_price", nullable = false)
    private boolean customPrice;

    /**
     * The plan this flat rate was copied from when the company was put on it (PD-79), or null when the
     * rate is the published list or was agreed by hand. Only meaningful with {@link #customPrice} set:
     * a plan price is a flat rate like an agreed one, but it follows the plan when the plan changes,
     * where an agreed rate stays put.
     */
    @Column(name = "price_plan_code", length = 32)
    private String pricePlanCode;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    /** The last month ({@code YYYY-MM}) that has been paid for; earlier months read as paid. */
    @Column(name = "paid_through", length = 7)
    private String paidThrough;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    /** Seat limit the company is paying for (Netflix-style); headcount must fit within it. */
    @Column(nullable = false)
    private int seats = 5;

    /** The date the subscription runs to; past this (or a CANCELLED status) the app is locked. */
    @Column(name = "ends_at")
    private java.time.LocalDate endsAt;

    /** The end date the "ends in 5 days" reminder was sent for (PD-76); a new end date reminds again. */
    @Column(name = "expiry_reminded_for")
    private java.time.LocalDate expiryRemindedFor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public Subscription(UUID id, UUID companyId, BigDecimal pricePerEmployee, String currency,
                        Instant trialEndsAt) {
        this.id = id;
        this.companyId = companyId;
        this.pricePerEmployee = pricePerEmployee;
        this.currency = currency;
        this.trialEndsAt = trialEndsAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getPlan() { return plan; }
    public boolean isCustomPrice() { return customPrice; }

    public void setCustomPrice(boolean customPrice) { this.customPrice = customPrice; }

    public String getPricePlanCode() { return pricePlanCode; }
    public void setPricePlanCode(String pricePlanCode) { this.pricePlanCode = pricePlanCode; }

    /** A flat rate copied from the company's plan, not negotiated. */
    public boolean isPlanPrice() { return customPrice && pricePlanCode != null; }

    /** A flat rate agreed with this customer by hand — the one thing choosing a plan never touches. */
    public boolean isAgreedPrice() { return customPrice && pricePlanCode == null; }

    // Charges are computed by PricingService, not here: the rate depends on the price list in force
    // for the month being billed, which is data this entity has no business reaching for.

    public BigDecimal getPricePerEmployee() { return pricePerEmployee; }
    public void setPricePerEmployee(BigDecimal pricePerEmployee) { this.pricePerEmployee = pricePerEmployee; }
    public String getCurrency() { return currency; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public String getPaidThrough() { return paidThrough; }
    public void setPaidThrough(String paidThrough) { this.paidThrough = paidThrough; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }
    public java.time.LocalDate getEndsAt() { return endsAt; }
    public void setEndsAt(java.time.LocalDate endsAt) { this.endsAt = endsAt; }
    public java.time.LocalDate getExpiryRemindedFor() { return expiryRemindedFor; }
    public void setExpiryRemindedFor(java.time.LocalDate expiryRemindedFor) { this.expiryRemindedFor = expiryRemindedFor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * The app is locked when the subscription was never activated, the owner has ended it, or its end
     * date has passed. {@code PENDING} is included so a company an agency creates is unusable until the
     * platform owner turns billing on — the agency provisions, the vendor activates (PD-18).
     */
    public boolean isLocked() {
        return status == SubscriptionStatus.CANCELLED
                || status == SubscriptionStatus.PENDING
                || (endsAt != null && endsAt.isBefore(java.time.LocalDate.now()));
    }
}
