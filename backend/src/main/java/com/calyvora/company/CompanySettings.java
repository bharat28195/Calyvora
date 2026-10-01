package com.calyvora.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 1:1 configuration for a company. The primary key is the company id. */
@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    /**
     * The clock this company runs on: what "today" means, and the wall time a check-in is stamped
     * with.
     *
     * <p>Defaulted to UTC while the currency beside it defaulted to INR, which is a company that
     * exists nowhere. The consequence was not abstract — an India-based tenant checking in at 21:52
     * had "In at 16:22" written against their name, correct for UTC and wrong for them, and their
     * whole attendance record was shifted by five and a half hours. The two defaults now describe
     * the same company; anyone elsewhere sets it in Settings, as they already had to.
     */
    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false, length = 16)
    private String locale = "en";

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    /** Optional legal/registered name shown on the payslip header (falls back to the company name). */
    @Column(name = "legal_name", length = 160)
    private String legalName;

    /** Optional company address shown on the payslip header. */
    @Column(length = 300)
    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /**
     * Sign someone out after this many minutes with no activity. Null means never, which is the
     * default and what every company had before this existed.
     *
     * <p>A company setting rather than a deployment one because the answer is a policy, not a
     * technical limit: a payroll screen open on a shared desk in an office wants fifteen minutes,
     * and a five-person startup on their own laptops wants never to be asked again.
     */
    @Column(name = "session_idle_minutes")
    private Integer sessionIdleMinutes;

    /**
     * Whether employees may still change their tax declarations.
     *
     * <p>HR opens this in April and closes it before the last payroll of the year, so the figures
     * cannot move under a run that has already been filed with the department.
     */
    @Column(name = "tax_declarations_open", nullable = false)
    private boolean taxDeclarationsOpen = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanySettings() {
    }

    public CompanySettings(UUID companyId) {
        this.companyId = companyId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Integer getSessionIdleMinutes() { return sessionIdleMinutes; }
    public void setSessionIdleMinutes(Integer sessionIdleMinutes) { this.sessionIdleMinutes = sessionIdleMinutes; }

    public boolean isTaxDeclarationsOpen() { return taxDeclarationsOpen; }
    public void setTaxDeclarationsOpen(boolean taxDeclarationsOpen) { this.taxDeclarationsOpen = taxDeclarationsOpen; }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
