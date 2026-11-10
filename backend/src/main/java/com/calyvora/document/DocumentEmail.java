package com.calyvora.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One emailing of an issued letter: to whom, and whether it went (V74). */
@Entity
@Table(name = "document_emails")
public class DocumentEmail {

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "sent_to", nullable = false, length = 320)
    private String sentTo;
    @Column(nullable = false, length = 300)
    private String subject;
    @Column(name = "sent_by")
    private UUID sentBy;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();
    @Column(nullable = false)
    private boolean delivered;
    @Column(length = 500)
    private String error;

    protected DocumentEmail() {
    }

    public DocumentEmail(UUID companyId, UUID documentId, String sentTo, String subject, UUID sentBy,
                         boolean delivered, String error) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.documentId = documentId;
        this.sentTo = sentTo;
        this.subject = subject;
        this.sentBy = sentBy;
        this.delivered = delivered;
        this.error = error == null ? null : error.length() > 500 ? error.substring(0, 500) : error;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getSentTo() { return sentTo; }
    public String getSubject() { return subject; }
    public UUID getSentBy() { return sentBy; }
    public Instant getSentAt() { return sentAt; }
    public boolean isDelivered() { return delivered; }
    public String getError() { return error; }
}
