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

    @Column(nullable = false, length = 40)
    private String plan = "PER_EMPLOYEE";

    @Column(name = "price_per_employee", nullable = false)
    private BigDecimal pricePerEmployee;

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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** The app is locked when the owner has ended the subscription or its end date has passed. */
    public boolean isLocked() {
        return status == SubscriptionStatus.CANCELLED
                || (endsAt != null && endsAt.isBefore(java.time.LocalDate.now()));
    }
}
