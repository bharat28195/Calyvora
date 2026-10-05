package com.calyvora.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A file in the company documents area: a policy PDF, a form, a handbook. The bytes live elsewhere
 * (see {@link com.calyvora.knowledge.storage.FileStorage}); this row is what a folder listing reads.
 */
@Entity
@Table(name = "company_files")
public class CompanyFile {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** DB or R2: where the bytes were written, so a later switch of backend can still read old files. */
    @Column(nullable = false, length = 8)
    private String storage;

    @Column(name = "storage_key", length = 300)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompanyFile() {
    }

    public CompanyFile(UUID id, UUID companyId, UUID spaceId, String title, String fileName,
                       String contentType, long sizeBytes, UUID uploadedBy) {
        this.id = id;
        this.companyId = companyId;
        this.spaceId = spaceId;
        this.title = title;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getSpaceId() { return spaceId; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorage() { return storage; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStorage(String storage, String storageKey) {
        this.storage = storage;
        this.storageKey = storageKey;
    }
}
