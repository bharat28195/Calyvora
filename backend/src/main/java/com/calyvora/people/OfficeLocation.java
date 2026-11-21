package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A place the company counts as "at work" for check-in: a point and a radius (V86, PD-81). */
@Entity
@Table(name = "office_locations")
public class OfficeLocation {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OfficeLocation() {
    }

    public OfficeLocation(UUID id, UUID companyId, String name, double latitude, double longitude, int radiusM) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusM() { return radiusM; }
    public Instant getCreatedAt() { return createdAt; }

    public void update(String name, double latitude, double longitude, int radiusM) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
    }
}
