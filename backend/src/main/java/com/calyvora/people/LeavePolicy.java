package com.calyvora.people;

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
 * One company's rule for one kind of leave.
 *
 * <p>Replaces {@code VACATION_ALLOWANCE_DAYS = 25}, a constant that gave every company on the
 * platform the same holiday entitlement whether they agreed to it or not.
 */
@Entity
@Table(name = "leave_policies")
public class LeavePolicy {

    /** The defaults a company gets before anyone edits them — the old constant, made editable. */
    public static final BigDecimal DEFAULT_VACATION_DAYS = BigDecimal.valueOf(25);
    public static final int DEFAULT_COMP_OFF_EXPIRY_DAYS = 90;

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LeaveType type;

    @Column(nullable = false)
    private boolean paid = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaveAccrual accrual = LeaveAccrual.ANNUAL;

    @Column(name = "days_per_year", nullable = false, precision = 5, scale = 1)
    private BigDecimal daysPerYear = BigDecimal.ZERO;

    @Column(name = "carry_forward_cap", nullable = false, precision = 5, scale = 1)
    private BigDecimal carryForwardCap = BigDecimal.ZERO;

    @Column(name = "comp_off_expiry_days", nullable = false)
    private int compOffExpiryDays = DEFAULT_COMP_OFF_EXPIRY_DAYS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeavePolicy() {
    }

    public LeavePolicy(UUID id, UUID companyId, LeaveType type) {
        this.id = id;
        this.companyId = companyId;
        this.type = type;
    }

    /**
     * The policy a company has before it has one.
     *
     * <p>Vacation is 25 days granted annually with no carry-forward — exactly the behaviour the
     * hard-coded constant produced, so nothing changes for an existing company on the day policies
     * arrive. Every other type starts at zero rather than at a guessed number: showing a company an
     * allowance it never agreed to is worse than showing it none.
     */
    public static LeavePolicy defaultFor(UUID companyId, LeaveType type) {
        LeavePolicy p = new LeavePolicy(UUID.randomUUID(), companyId, type);
        p.paid = type != LeaveType.UNPAID;
        p.accrual = LeaveAccrual.ANNUAL;
        p.daysPerYear = type == LeaveType.VACATION ? DEFAULT_VACATION_DAYS : BigDecimal.ZERO;
        p.carryForwardCap = BigDecimal.ZERO;
        return p;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LeaveType getType() {
        return type;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public LeaveAccrual getAccrual() {
        return accrual;
    }

    public void setAccrual(LeaveAccrual accrual) {
        this.accrual = accrual;
    }

    public BigDecimal getDaysPerYear() {
        return daysPerYear;
    }

    public void setDaysPerYear(BigDecimal daysPerYear) {
        this.daysPerYear = daysPerYear;
    }

    public BigDecimal getCarryForwardCap() {
        return carryForwardCap;
    }

    public void setCarryForwardCap(BigDecimal carryForwardCap) {
        this.carryForwardCap = carryForwardCap;
    }

    public int getCompOffExpiryDays() {
        return compOffExpiryDays;
    }

    public void setCompOffExpiryDays(int compOffExpiryDays) {
        this.compOffExpiryDays = compOffExpiryDays;
    }
}
