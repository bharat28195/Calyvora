package com.calyvora.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day's remaining work in a sprint (V23). Recorded rather than computed, because a burndown
 * derived from current state can only ever draw today — history has to be captured as it happens.
 */
@Entity
@Table(name = "sprint_snapshots")
public class SprintSnapshot {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @Column(name = "on_date", nullable = false)
    private LocalDate date;

    @Column(name = "remaining_points", nullable = false)
    private int remainingPoints;

    @Column(name = "completed_points", nullable = false)
    private int completedPoints;

    @Column(name = "remaining_tasks", nullable = false)
    private int remainingTasks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SprintSnapshot() {
    }

    public SprintSnapshot(UUID id, UUID companyId, UUID sprintId, LocalDate date,
                          int remainingPoints, int completedPoints, int remainingTasks) {
        this.id = id;
        this.companyId = companyId;
        this.sprintId = sprintId;
        this.date = date;
        this.remainingPoints = remainingPoints;
        this.completedPoints = completedPoints;
        this.remainingTasks = remainingTasks;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public LocalDate getDate() { return date; }
    public int getRemainingPoints() { return remainingPoints; }
    public int getCompletedPoints() { return completedPoints; }
    public int getRemainingTasks() { return remainingTasks; }

    /** Re-recording the same day overwrites it — one row per sprint per day. */
    public void update(int remainingPoints, int completedPoints, int remainingTasks) {
        this.remainingPoints = remainingPoints;
        this.completedPoints = completedPoints;
        this.remainingTasks = remainingTasks;
    }
}
