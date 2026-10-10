package com.calyvora.tax;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A proof file attached to a declared line, a rent period, a house or the previous employer (V71). */
@Entity
@Table(name = "tax_proofs")
public class TaxProof {

    /** What a proof belongs to. */
    public enum Owner { ITEM, RENT, HOUSE, PREVIOUS }

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;
    @Column(name = "owner_type", nullable = false, length = 12)
    private String ownerType;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;
    /** Lazy: listing a declaration's proofs must not drag megabytes of PDF along with it. */
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TaxProof() {
    }

    public TaxProof(UUID companyId, UUID declarationId, Owner owner, UUID ownerId, String fileName,
                    String contentType, byte[] content, UUID uploadedBy) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.declarationId = declarationId;
        this.ownerType = owner.name();
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() { return id; }
    public UUID getDeclarationId() { return declarationId; }
    public Owner getOwner() { return Owner.valueOf(ownerType); }
    public UUID getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
