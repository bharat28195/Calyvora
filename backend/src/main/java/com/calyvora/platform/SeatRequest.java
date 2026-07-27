package com.calyvora.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A company admin's request for more seats (Netflix-style upgrade). The platform owner approves it in
 * the console, which bumps the subscription's seat limit. Platform-managed (not tenant-isolated).
 */
@Entity
@Table(name = "seat_requests")
public class SeatRequest {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "requested_seats", nullable = false)
    private int requestedSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatRequestStatus status = SeatRequestStatus.PENDING;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected SeatRequest() {
    }

    public SeatRequest(UUID id, UUID companyId, int requestedSeats, String note) {
        this.id = id;
        this.companyId = companyId;
        this.requestedSeats = requestedSeats;
        this.note = note;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public int getRequestedSeats() { return requestedSeats; }
    public SeatRequestStatus getStatus() { return status; }
    public void setStatus(SeatRequestStatus status) { this.status = status; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
