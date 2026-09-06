package com.calyvora.feature;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A named set of features with a price — what a customer buys. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    /**
     * Per employee per month, in the company's own currency.
     *
     * <p>Null means "charge the published price list". A plan can therefore restrict features without
     * changing anybody's bill, which is exactly what a grandfathered customer needs.
     */
    @Column(name = "price_per_employee", precision = 10, scale = 2)
    private BigDecimal pricePerEmployee;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Eagerly fetched, deliberately. A plan has at most a dozen features and is read on every request
     * that checks one; lazy loading here would trade a join for an N+1 in the hot path, and the whole
     * point of the feature check is that it is cheap enough to run everywhere.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_code"))
    @Column(name = "feature", nullable = false, length = 48)
    @Enumerated(EnumType.STRING)
    private Set<Feature> features = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Plan() {
    }

    public Plan(String code, String name) {
        this.code = code;
        this.name = name;
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

    public boolean includes(Feature feature) {
        return features.contains(feature);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPricePerEmployee() {
        return pricePerEmployee;
    }

    public void setPricePerEmployee(BigDecimal pricePerEmployee) {
        this.pricePerEmployee = pricePerEmployee;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(Set<Feature> features) {
        this.features = features;
    }
}
