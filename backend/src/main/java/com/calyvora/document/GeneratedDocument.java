package com.calyvora.document;

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
 * A rendered letter (feedback D2). The body is frozen at generation time — editing the template
 * afterwards never rewrites history, which is what makes an issued letter trustworthy.
 */
@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentKind kind = DocumentKind.CUSTOM;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GeneratedDocument() {
    }

    public GeneratedDocument(UUID id, UUID companyId, UUID templateId, UUID employeeId, String title,
                             DocumentKind kind, String body, UUID generatedBy) {
        this.id = id;
        this.companyId = companyId;
        this.templateId = templateId;
        this.employeeId = employeeId;
        this.title = title;
        this.kind = kind;
        this.body = body;
        this.generatedBy = generatedBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getTemplateId() { return templateId; }
    public UUID getEmployeeId() { return employeeId; }
    public String getTitle() { return title; }
    public DocumentKind getKind() { return kind; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
