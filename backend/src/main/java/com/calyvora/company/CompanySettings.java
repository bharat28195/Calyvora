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

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
