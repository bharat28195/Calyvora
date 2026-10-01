package com.calyvora.feature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One capability, switched on or off for one company.
 *
 * <p>A missing row means off. Storing only what has been deliberately changed keeps the table small
 * and makes "has anybody actually enabled this yet?" a one-line query.
 */
@Entity
@Table(name = "company_features")
public class CompanyFeature {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private Feature feature;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyFeature() {
    }

    public CompanyFeature(UUID id, UUID companyId, Feature feature, boolean enabled) {
        this.id = id;
        this.companyId = companyId;
        this.feature = feature;
        this.enabled = enabled;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Feature getFeature() {
        return feature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
