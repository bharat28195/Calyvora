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
 * Someone authorised to sign the company's letters, with their signature image (PD-71, V79).
 *
 * <p>Switched off rather than deleted: a letter issued under a signatory keeps printing that
 * signature, so the row and its image must outlive the person's authority to sign new ones.
 */
@Entity
@Table(name = "letter_signatories")
public class LetterSignatory {

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 120)
    private String title;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image")
    private byte[] image;
    @Column(name = "image_type", length = 64)
    private String imageType;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LetterSignatory() {
    }

    public LetterSignatory(UUID companyId, String name, String title) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.name = name;
        this.title = title;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; touch(); }
    public byte[] getImage() { return image; }
    public String getImageType() { return imageType; }
    public boolean hasImage() { return imageType != null; }
    public void setImage(byte[] image, String type) { this.image = image; this.imageType = image == null ? null : type; touch(); }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; touch(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; if (!active) this.isDefault = false; touch(); }
    public Instant getUpdatedAt() { return updatedAt; }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
