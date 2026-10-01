package com.calyvora.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Something a client has requested/asked for (feedback D1 — "what they have requested"). */
@Entity
@Table(name = "client_requests")
public class ClientRequest {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RequestStatus status = RequestStatus.REQUESTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClientRequest() {
    }

    public ClientRequest(UUID id, UUID companyId, UUID clientId, String title, String description) {
        this.id = id;
        this.companyId = companyId;
        this.clientId = clientId;
        this.title = title;
        this.description = description;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getClientId() { return clientId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
