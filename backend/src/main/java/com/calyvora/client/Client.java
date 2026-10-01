package com.calyvora.client;

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

/** A customer/client of the company (Clients module — feedback D1). Anemic; rules in {@link ClientService}. */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "contact_name", length = 160)
    private String contactName;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(length = 40)
    private String phone;

    @Column(length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClientStatus status = ClientStatus.LEAD;

    @Column(length = 4000)
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Client() {
    }

    public Client(UUID id, UUID companyId, String name, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public ClientStatus getStatus() { return status; }
    public void setStatus(ClientStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
