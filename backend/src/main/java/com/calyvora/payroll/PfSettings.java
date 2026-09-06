package com.calyvora.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One company's Provident Fund rates.
 *
 * <p>Rates live in a row rather than in constants because they are set by statute and statutes
 * change — the wage ceiling has moved before and will move again. A rate change should be an UPDATE
 * and a line in the release notes, not a redeploy.
 *
 * <p>The defaults are the rates in force in 2026, so a company that never opens this screen gets
 * lawful figures.
 */
@Entity
@Table(name = "pf_settings")
public class PfSettings {

    public static final BigDecimal DEFAULT_WAGE_CEILING = BigDecimal.valueOf(15000);

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "wage_ceiling", nullable = false, precision = 12, scale = 2)
    private BigDecimal wageCeiling = DEFAULT_WAGE_CEILING;

    /**
     * Cap contributions at the ceiling (the common choice), or compute on actual PF wages however
     * high they run. Both are lawful, and the difference is thousands of rupees a month per employee —
     * which is why it is a setting rather than an assumption baked into the calculator.
     */
    @Column(name = "restrict_to_ceiling", nullable = false)
    private boolean restrictToCeiling = true;

    @Column(name = "employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeRate = BigDecimal.valueOf(12);

    @Column(name = "employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employerRate = BigDecimal.valueOf(12);

    /** Carved OUT of the employer's rate, never added to it. */
    @Column(name = "eps_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal epsRate = BigDecimal.valueOf(8.33);

    @Column(name = "admin_charge_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal adminChargeRate = BigDecimal.valueOf(0.50);

    @Column(name = "edli_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal edliRate = BigDecimal.valueOf(0.50);

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PfSettings() {
    }

    public PfSettings(UUID companyId) {
        this.companyId = companyId;
    }

    /** The statutory defaults, for a company with no row of its own. */
    public static PfSettings defaults(UUID companyId) {
        return new PfSettings(companyId);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public BigDecimal getWageCeiling() {
        return wageCeiling;
    }

    public void setWageCeiling(BigDecimal wageCeiling) {
        this.wageCeiling = wageCeiling;
    }

    public boolean isRestrictToCeiling() {
        return restrictToCeiling;
    }

    public void setRestrictToCeiling(boolean restrictToCeiling) {
        this.restrictToCeiling = restrictToCeiling;
    }

    public BigDecimal getEmployeeRate() {
        return employeeRate;
    }

    public void setEmployeeRate(BigDecimal employeeRate) {
        this.employeeRate = employeeRate;
    }

    public BigDecimal getEmployerRate() {
        return employerRate;
    }

    public void setEmployerRate(BigDecimal employerRate) {
        this.employerRate = employerRate;
    }

    public BigDecimal getEpsRate() {
        return epsRate;
    }

    public void setEpsRate(BigDecimal epsRate) {
        this.epsRate = epsRate;
    }

    public BigDecimal getAdminChargeRate() {
        return adminChargeRate;
    }

    public void setAdminChargeRate(BigDecimal adminChargeRate) {
        this.adminChargeRate = adminChargeRate;
    }

    public BigDecimal getEdliRate() {
        return edliRate;
    }

    public void setEdliRate(BigDecimal edliRate) {
        this.edliRate = edliRate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
