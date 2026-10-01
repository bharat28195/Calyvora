package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An org unit. Departments nest via {@code parentId}; {@code leadUserId} is an optional head. */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Department() {
    }

    public Department(UUID id, UUID companyId, String name) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
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

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getLeadUserId() {
        return leadUserId;
    }

    public void setLeadUserId(UUID leadUserId) {
        this.leadUserId = leadUserId;
    }
}
