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

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

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

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
