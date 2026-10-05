package com.calyvora.knowledge;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The bytes of a {@link CompanyFile} stored in the database (the default, until R2 is configured). */
@Entity
@Table(name = "company_file_blobs")
public class CompanyFileBlob {

    @Id
    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    protected CompanyFileBlob() {
    }

    public CompanyFileBlob(UUID fileId, UUID companyId, byte[] data) {
        this.fileId = fileId;
        this.companyId = companyId;
        this.data = data;
    }

    public UUID getFileId() { return fileId; }
    public byte[] getData() { return data; }
}
