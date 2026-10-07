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
 * One company's ESI and professional-tax switches, the ESI rates, and the registration numbers the
 * statutory files carry. PF keeps its own row ({@link PfSettings}); this is everything else.
 *
 * <p>Defaults are the rates in force in 2026, so a company that never opens the screen still gets
 * lawful figures once it switches ESI on.
 */
@Entity
@Table(name = "statutory_settings")
public class StatutorySettings {

    public static final BigDecimal DEFAULT_ESI_EMPLOYEE_RATE = new BigDecimal("0.75");
    public static final BigDecimal DEFAULT_ESI_EMPLOYER_RATE = new BigDecimal("3.25");
    public static final BigDecimal DEFAULT_ESI_WAGE_CEILING = new BigDecimal("21000");

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "esi_enabled", nullable = false)
    private boolean esiEnabled;

    @Column(name = "esi_employee_rate", nullable = false)
    private BigDecimal esiEmployeeRate = DEFAULT_ESI_EMPLOYEE_RATE;

    @Column(name = "esi_employer_rate", nullable = false)
    private BigDecimal esiEmployerRate = DEFAULT_ESI_EMPLOYER_RATE;

    @Column(name = "esi_wage_ceiling", nullable = false)
    private BigDecimal esiWageCeiling = DEFAULT_ESI_WAGE_CEILING;

    @Column(name = "pt_enabled", nullable = false)
    private boolean ptEnabled;

    @Column(name = "pf_establishment_code", length = 32)
    private String pfEstablishmentCode;

    @Column(name = "esi_employer_code", length = 32)
    private String esiEmployerCode;

    @Column(name = "tan", length = 16)
    private String tan;

    @Column(name = "company_pan", length = 16)
    private String companyPan;

    @Column(name = "pt_registration_no", length = 48)
    private String ptRegistrationNo;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StatutorySettings() {
    }

    public StatutorySettings(UUID companyId) {
        this.companyId = companyId;
    }

    public static StatutorySettings defaults(UUID companyId) {
        return new StatutorySettings(companyId);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getCompanyId() { return companyId; }

    public boolean isEsiEnabled() { return esiEnabled; }
    public void setEsiEnabled(boolean esiEnabled) { this.esiEnabled = esiEnabled; }

    public BigDecimal getEsiEmployeeRate() { return esiEmployeeRate; }
    public void setEsiEmployeeRate(BigDecimal v) { this.esiEmployeeRate = v; }

    public BigDecimal getEsiEmployerRate() { return esiEmployerRate; }
    public void setEsiEmployerRate(BigDecimal v) { this.esiEmployerRate = v; }

    public BigDecimal getEsiWageCeiling() { return esiWageCeiling; }
    public void setEsiWageCeiling(BigDecimal v) { this.esiWageCeiling = v; }

    public boolean isPtEnabled() { return ptEnabled; }
    public void setPtEnabled(boolean ptEnabled) { this.ptEnabled = ptEnabled; }

    public String getPfEstablishmentCode() { return pfEstablishmentCode; }
    public void setPfEstablishmentCode(String v) { this.pfEstablishmentCode = v; }

    public String getEsiEmployerCode() { return esiEmployerCode; }
    public void setEsiEmployerCode(String v) { this.esiEmployerCode = v; }

    public String getTan() { return tan; }
    public void setTan(String v) { this.tan = v; }

    public String getCompanyPan() { return companyPan; }
    public void setCompanyPan(String v) { this.companyPan = v; }

    public String getPtRegistrationNo() { return ptRegistrationNo; }
    public void setPtRegistrationNo(String v) { this.ptRegistrationNo = v; }
}
