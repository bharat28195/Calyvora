package com.calyvora.document;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A letter sent to the person it is for, to accept with their signature or to decline (PD-72, V80).
 *
 * <p>The record is the evidence: the fingerprint of the exact text that was sent, when it was opened,
 * and — when signed — the drawn signature, the name typed, the time, the IP address and the browser.
 */
@Entity
@Table(name = "signature_requests")
public class SignatureRequest {

    public enum Status { SENT, VIEWED, SIGNED, DECLINED, WITHDRAWN }

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "employee_id")
    private UUID employeeId;
    @Column(name = "signer_name", nullable = false, length = 200)
    private String signerName;
    @Column(name = "signer_email", length = 320)
    private String signerEmail;
    @Column(nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.SENT;
    @Column(name = "document_hash", nullable = false, length = 64)
    private String documentHash;
    @Column(columnDefinition = "text")
    private String message;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "sent_by")
    private UUID sentBy;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();
    @Column(name = "viewed_at")
    private Instant viewedAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decline_reason", length = 500)
    private String declineReason;
    @Column(name = "typed_name", length = 200)
    private String typedName;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "signature_image")
    private byte[] signatureImage;
    @Column(name = "signature_type", length = 64)
    private String signatureType;
    @Column(name = "signer_ip", length = 64)
    private String signerIp;
    @Column(name = "signer_agent", length = 400)
    private String signerAgent;
    @Column(nullable = false)
    private int reminders;
    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    protected SignatureRequest() {
    }

    public SignatureRequest(UUID companyId, UUID documentId, UUID employeeId, String signerName, String signerEmail,
                            String documentHash, String message, Instant expiresAt, UUID sentBy) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.documentId = documentId;
        this.employeeId = employeeId;
        this.signerName = signerName;
        this.signerEmail = signerEmail;
        this.documentHash = documentHash;
        this.message = message;
        this.expiresAt = expiresAt;
        this.sentBy = sentBy;
    }

    /** Still waiting for an answer and not past its date. */
    public boolean isOpen(Instant now) {
        return (status == Status.SENT || status == Status.VIEWED) && now.isBefore(expiresAt);
    }

    public void viewed(Instant at) {
        if (status == Status.SENT) {
            status = Status.VIEWED;
            viewedAt = at;
        }
    }

    public void sign(String typedName, byte[] png, String ip, String agent, Instant at) {
        if (viewedAt == null) viewedAt = at;
        this.status = Status.SIGNED;
        this.typedName = typedName;
        this.signatureImage = png;
        this.signatureType = "image/png";
        this.signerIp = ip;
        this.signerAgent = agent;
        this.decidedAt = at;
    }

    public void decline(String reason, String ip, String agent, Instant at) {
        if (viewedAt == null) viewedAt = at;
        this.status = Status.DECLINED;
        this.declineReason = reason;
        this.signerIp = ip;
        this.signerAgent = agent;
        this.decidedAt = at;
    }

    public void withdraw(Instant at) {
        this.status = Status.WITHDRAWN;
        this.decidedAt = at;
    }

    public void reminded(Instant at, Instant newExpiry) {
        this.reminders++;
        this.lastRemindedAt = at;
        if (newExpiry.isAfter(expiresAt)) this.expiresAt = newExpiry;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getDocumentId() { return documentId; }
    public UUID getEmployeeId() { return employeeId; }
    public String getSignerName() { return signerName; }
    public String getSignerEmail() { return signerEmail; }
    public Status getStatus() { return status; }
    public String getDocumentHash() { return documentHash; }
    public String getMessage() { return message; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getSentBy() { return sentBy; }
    public Instant getSentAt() { return sentAt; }
    public Instant getViewedAt() { return viewedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDeclineReason() { return declineReason; }
    public String getTypedName() { return typedName; }
    public byte[] getSignatureImage() { return signatureImage; }
    public String getSignerIp() { return signerIp; }
    public String getSignerAgent() { return signerAgent; }
    public int getReminders() { return reminders; }
    public Instant getLastRemindedAt() { return lastRemindedAt; }
}
