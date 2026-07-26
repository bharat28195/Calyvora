package com.calyvora.recruit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A job opening (requisition) the company is hiring for. Candidates attach to it. */
@Entity
@Table(name = "job_openings")
public class JobOpening {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(length = 120)
    private String location;

    @Column(name = "employment_type", length = 24)
    private String employmentType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int positions = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status = JobStatus.OPEN;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobOpening() {
    }

    public JobOpening(UUID id, UUID companyId, String title, UUID departmentId, String location,
                      String employmentType, String description, int positions, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.title = title;
        this.departmentId = departmentId;
        this.location = location;
        this.employmentType = employmentType;
        this.description = description;
        this.positions = positions;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPositions() { return positions; }
    public void setPositions(int positions) { this.positions = positions; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
