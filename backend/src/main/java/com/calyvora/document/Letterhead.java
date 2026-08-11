package com.calyvora.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A company's letterpad (PD-20): the logo, type and colour a letter is printed on, plus the address
 * block at the top and the strip at the bottom.
 *
 * <p>One row per company — the whole point is that every letter comes out on the same stationery, so
 * there is nothing to choose between at generation time. Anemic; rules live in
 * {@link LetterheadService}.
 */
@Entity
@Table(name = "letterheads")
public class Letterhead {

    /** The company owns exactly one, so its id is the primary key. */
    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Blank means "use the company's name" — resolved at render time, not stored. */
    @Column(length = 160)
    private String heading;

    @Column(name = "address_lines", columnDefinition = "text")
    private String addressLines;

    @Column(name = "footer_text", columnDefinition = "text")
    private String footerText;

    @Column(name = "brand_color", nullable = false, length = 9)
    private String brandColor = "#7c5cff";

    @Column(name = "font_family", nullable = false, length = 24)
    private String fontFamily = "SERIF";

    @Column(name = "show_divider", nullable = false)
    private boolean showDivider = true;

    @Column(name = "signature_name", length = 120)
    private String signatureName;

    @Column(name = "signature_title", length = 120)
    private String signatureTitle;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Letterhead() {
    }

    public Letterhead(UUID companyId) {
        this.companyId = companyId;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getCompanyId() { return companyId; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getAddressLines() { return addressLines; }
    public void setAddressLines(String addressLines) { this.addressLines = addressLines; }
    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }
    public String getBrandColor() { return brandColor; }
    public void setBrandColor(String brandColor) { this.brandColor = brandColor; }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public boolean isShowDivider() { return showDivider; }
    public void setShowDivider(boolean showDivider) { this.showDivider = showDivider; }
    public String getSignatureName() { return signatureName; }
    public void setSignatureName(String signatureName) { this.signatureName = signatureName; }
    public String getSignatureTitle() { return signatureTitle; }
    public void setSignatureTitle(String signatureTitle) { this.signatureTitle = signatureTitle; }
    public Instant getUpdatedAt() { return updatedAt; }
}
