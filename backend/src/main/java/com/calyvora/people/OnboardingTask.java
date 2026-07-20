package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A single onboarding checklist item for an employee (People OS slice P3). */
@Entity
@Table(name = "onboarding_tasks")
public class OnboardingTask {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OnboardingTask() {
    }

    public OnboardingTask(UUID id, UUID companyId, UUID employeeId, String title, int sortOrder) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.title = title;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        this.completedAt = completed ? Instant.now() : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getTitle() {
        return title;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
