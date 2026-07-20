package com.calyvora.work;

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

/** A container for tasks (Work OS slice W1). {@code key} prefixes human-friendly task numbers. */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 10)
    private String key;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(UUID id, UUID companyId, String name, String key, String description, UUID leadUserId) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.key = key;
        this.description = description;
        this.leadUserId = leadUserId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public UUID getLeadUserId() {
        return leadUserId;
    }

    public void setLeadUserId(UUID leadUserId) {
        this.leadUserId = leadUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
