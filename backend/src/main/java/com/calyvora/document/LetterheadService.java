package com.calyvora.document;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.document.dto.LetterheadPayload;
import com.calyvora.document.dto.LetterheadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The company letterpad (PD-20). Read on every letter preview, written from one screen.
 *
 * <p>Reading is get-or-create rather than "404 until configured": a company that has never opened
 * the screen still has a letterpad — its own name, the default type and colour — so the first letter
 * it ever issues is already headed properly. Nothing about the feature requires setup to work.
 */
@Service
public class LetterheadService {

    private final LetterheadRepository letterheadRepository;
    private final CompanyRepository companyRepository;

    public LetterheadService(LetterheadRepository letterheadRepository, CompanyRepository companyRepository) {
        this.letterheadRepository = letterheadRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public LetterheadResponse get() {
        return LetterheadResponse.of(getOrCreate(), companyName());
    }

    @Transactional
    public LetterheadResponse update(LetterheadPayload p) {
        Letterhead l = getOrCreate();
        // PATCH semantics throughout: null leaves the field alone, blank clears it. A full replace
        // here once wiped the details printed on payslips (PD-16) — the same mistake costs more on a
        // letterhead, where the loss is silent until someone reads a letter that went out headless.
        if (p.logoUrl() != null) l.setLogoUrl(blankToNull(p.logoUrl()));
        if (p.heading() != null) l.setHeading(blankToNull(p.heading()));
        if (p.addressLines() != null) l.setAddressLines(blankToNull(p.addressLines()));
        if (p.footerText() != null) l.setFooterText(blankToNull(p.footerText()));
        if (p.brandColor() != null) l.setBrandColor(p.brandColor());
        if (p.fontFamily() != null) l.setFontFamily(p.fontFamily());
        if (p.showDivider() != null) l.setShowDivider(p.showDivider());
        if (p.signatureName() != null) l.setSignatureName(blankToNull(p.signatureName()));
        if (p.signatureTitle() != null) l.setSignatureTitle(blankToNull(p.signatureTitle()));
        if (p.useBackground() != null) {
            if (p.useBackground() && l.getBackgroundName() == null) {
                throw new com.calyvora.common.error.ApiException(
                        com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                        "Upload a letterpad before switching it on.");
            }
            l.setUseBackground(p.useBackground());
        }
        letterheadRepository.save(l);
        return LetterheadResponse.of(l, companyName());
    }

    /** The letterpad as a letter render needs it, including the resolved heading. */
    @Transactional
    public LetterheadResponse forRender() {
        return get();
    }

    /**
     * Accepted letterpad formats.
     *
     * <p>Images only. A PDF letterpad is the commoner thing for a printer to hand over, and it
     * cannot be used as a CSS background without rasterising it — which needs a renderer this
     * application does not have. Refusing it with a sentence that says what to do instead is
     * better than accepting it and printing a broken page.
     */
    private static final java.util.Set<String> ACCEPTED =
            java.util.Set.of("image/png", "image/jpeg", "image/webp");

    /** Two megabytes: comfortably a 300-dpi A4 scan, and small enough to sit in a row. */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    @Transactional
    public LetterheadResponse uploadBackground(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR, "Choose a file to upload.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!ACCEPTED.contains(type)) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                    "Upload a PNG, JPEG or WebP image. A PDF letterpad needs exporting to an image first.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                    "That file is larger than 2 MB. Export it at a lower resolution and try again.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR, "That file could not be read.");
        }
        Letterhead l = getOrCreate();
        l.setBackground(bytes, type, blankToNull(file.getOriginalFilename()) == null
                ? "letterpad" : file.getOriginalFilename());
        // Uploading is the act of choosing it. Making somebody upload and then find a switch would
        // be two steps for one intention.
        l.setUseBackground(true);
        letterheadRepository.save(l);
        return LetterheadResponse.of(l, companyName());
    }

    @Transactional
    public LetterheadResponse removeBackground() {
        Letterhead l = getOrCreate();
        l.setBackground(null, null, null);
        letterheadRepository.save(l);
        return LetterheadResponse.of(l, companyName());
    }

    /** The uploaded bytes, for the endpoint that serves them. Empty when there is no letterpad. */
    @Transactional(readOnly = true)
    public java.util.Optional<StoredImage> background() {
        return letterheadRepository.findById(TenantContext.getCompanyId())
                .filter(l -> l.getBackgroundImage() != null)
                .map(l -> new StoredImage(l.getBackgroundImage(), l.getBackgroundType(),
                        l.getUpdatedAt().toEpochMilli()));
    }

    /** @param version the letterpad's last-modified stamp, used to bust a cached image. */
    public record StoredImage(byte[] bytes, String contentType, long version) {}

    private Letterhead getOrCreate() {
        UUID companyId = TenantContext.getCompanyId();
        return letterheadRepository.findById(companyId)
                .orElseGet(() -> letterheadRepository.save(new Letterhead(companyId)));
    }

    private String companyName() {
        return companyRepository.findById(TenantContext.getCompanyId())
                .map(com.calyvora.company.Company::getName)
                .orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
