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
        if (p.cin() != null) l.setCin(blankToNull(p.cin()));
        if (p.gstin() != null) l.setGstin(blankToNull(p.gstin()));
        if (p.website() != null) l.setWebsite(blankToNull(p.website()));
        if (p.email() != null) l.setEmail(blankToNull(p.email()));
        if (p.dateStyle() != null) l.setDateStyle(p.dateStyle());
        if (p.probationDays() != null) l.setProbationDays(p.probationDays() == 0 ? null : p.probationDays());
        if (p.noticeProbation() != null) l.setNoticeProbation(blankToNull(p.noticeProbation()));
        if (p.noticePeriod() != null) l.setNoticePeriod(blankToNull(p.noticePeriod()));
        if (p.workingDays() != null) l.setWorkingDays(blankToNull(p.workingDays()));
        if (p.workingHours() != null) l.setWorkingHours(blankToNull(p.workingHours()));
        if (p.payDay() != null) l.setPayDay(blankToNull(p.payDay()));
        if (p.jurisdiction() != null) l.setJurisdiction(blankToNull(p.jurisdiction()));
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
     * Accepted letterpad formats: an image as it is, or a PDF or Word file whose first page is
     * rendered to an image (PD-64, see {@link LetterpadConverter}). The old Word format (.doc) is not
     * readable here; the message says to save it as .docx or PDF.
     */
    private static final java.util.Set<String> IMAGES = java.util.Set.of("image/png", "image/jpeg", "image/webp");
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Two megabytes for an image: comfortably a 300-dpi A4 scan, and small enough to sit in a row. */
    static final long MAX_BYTES = 2L * 1024 * 1024;
    /** A PDF or Word file can carry fonts and vector art, so it may be larger; what is stored is the image. */
    static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

    @Transactional
    public LetterheadResponse uploadBackground(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR, "Choose a file to upload.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        // Browsers send Word files under several types (or none); the extension settles it.
        if (name.endsWith(".pdf")) type = PDF;
        else if (name.endsWith(".docx")) type = DOCX;
        if (name.endsWith(".doc")) {
            throw invalid("That is the old Word format. Save it as .docx or as a PDF, and upload that.");
        }
        boolean document = PDF.equals(type) || DOCX.equals(type);
        if (!IMAGES.contains(type) && !document) {
            throw invalid("Upload your letterpad as a PDF, a Word file (.docx) or an image (PNG, JPEG or WebP).");
        }
        if (file.getSize() > (document ? MAX_DOCUMENT_BYTES : MAX_BYTES)) {
            throw invalid(document ? "That file is larger than 10 MB." : "That image is larger than 2 MB. Export it at a lower resolution and try again.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
            if (document) {
                LetterpadConverter.Image image = PDF.equals(type)
                        ? LetterpadConverter.fromPdf(bytes) : LetterpadConverter.fromDocx(bytes);
                bytes = image.bytes();
                type = image.contentType();
            }
        } catch (java.io.IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(LetterheadService.class)
                    .warn("Letterpad {} could not be converted: {}", file.getOriginalFilename(), e.toString(), e);
            throw invalid(document
                    ? "That file could not be read as a letterpad. If it is password-protected, remove the password; otherwise try saving it as a PDF."
                    : "That file could not be read.");
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

    /** The stored letterpad, created on first use — for the letter renderer's merge values. */
    @Transactional
    public Letterhead entity() {
        return getOrCreate();
    }

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

    private static com.calyvora.common.error.ApiException invalid(String message) {
        return new com.calyvora.common.error.ApiException(com.calyvora.common.error.ErrorCode.VALIDATION_ERROR, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
