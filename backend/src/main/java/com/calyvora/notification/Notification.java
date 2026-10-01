package com.calyvora.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One thing one person needs to know about (feedback D4/D5). Title/body/link are frozen at send time
 * so an inbox entry still reads correctly after the thing it points at changes.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 600)
    private String body;

    @Column(length = 300)
    private String link;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID id, UUID companyId, UUID recipientId, UUID actorId, NotificationType type,
                        String title, String body, String link, String entityType, UUID entityId) {
        this.id = id;
        this.companyId = companyId;
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type.name();
        this.title = title;
        this.body = body;
        this.link = link;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getRecipientId() { return recipientId; }
    public UUID getActorId() { return actorId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Instant getReadAt() { return readAt; }
    public void markRead() { if (readAt == null) readAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
}
