package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A company holiday. Resolves the attendance day for everyone unless marked otherwise. */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "on_date", nullable = false)
    private LocalDate date;

    /** Offered but not automatic — an optional holiday doesn't close the office. */
    @Column(nullable = false)
    private boolean optional;

    @Column(length = 400)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Holiday() {
    }

    public Holiday(UUID id, UUID companyId, String name, LocalDate date, boolean optional,
                   String note, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.date = date;
        this.optional = optional;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
