package com.calyvora.document;

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

/** A reusable letter template with {{merge.fields}} (feedback D3). Anemic; rules in {@link DocumentService}. */
@Entity
@Table(name = "document_templates")
public class DocumentTemplate {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentKind kind = DocumentKind.CUSTOM;

    @Column(length = 400)
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** True for the starter templates seeded on first use — still fully editable. */
    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentTemplate() {
    }

    public DocumentTemplate(UUID id, UUID companyId, String name, DocumentKind kind, String body, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.kind = kind;
        this.body = body;
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
    public DocumentKind getKind() { return kind; }
    public void setKind(DocumentKind kind) { this.kind = kind; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
