package com.calyvora.document;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorised signatories and the company seal (PD-71).
 *
 * <p>A letter is signed by the signatory its template names, else the company's default signatory,
 * else — as before PD-71 — the name typed on the letterpad or the person issuing it. When the
 * signatory has a signature image, a {@link #SIGNATURE_LINE} marker goes into the letter just above
 * the signatory's name; the screen and the PDF draw the signature (and the seal) there. The marker is
 * text in the stored letter, so the letter remembers where its signature goes; the image itself is
 * looked up by the signatory recorded on the letter.
 */
@Service
public class SignatoryService {

    /** The line in a letter's body where the signature (and seal) are drawn. */
    public static final String SIGNATURE_LINE = "[[signature]]";

    public record SignatoryView(String id, String name, String title, boolean hasImage, boolean isDefault,
                                boolean active, String updatedAt) {
        static SignatoryView of(LetterSignatory s) {
            return new SignatoryView(s.getId().toString(), s.getName(), s.getTitle(), s.hasImage(), s.isDefault(),
                    s.isActive(), s.getUpdatedAt().toString());
        }
    }

    public record SignatoryPayload(String name, String title, Boolean isDefault, Boolean active) {
    }

    public record StoredImage(byte[] bytes, String contentType, long version) {
    }

    private final LetterSignatoryRepository signatories;
    private final LetterheadRepository letterheads;

    public SignatoryService(LetterSignatoryRepository signatories, LetterheadRepository letterheads) {
        this.signatories = signatories;
        this.letterheads = letterheads;
    }

    @Transactional(readOnly = true)
    public List<SignatoryView> list() {
        return signatories.findByCompanyIdOrderByCreatedAtAsc(TenantContext.getCompanyId()).stream()
                .map(SignatoryView::of).toList();
    }

    @Transactional
    public SignatoryView create(SignatoryPayload p) {
        UUID companyId = TenantContext.getCompanyId();
        String name = required(p.name(), "Enter the signatory's name.", 120);
        LetterSignatory s = new LetterSignatory(companyId, name, optional(p.title(), 120));
        // The first one is the default; there is nobody else to sign.
        boolean first = signatories.findByCompanyIdOrderByCreatedAtAsc(companyId).stream().noneMatch(LetterSignatory::isActive);
        signatories.save(s);
        if (first || Boolean.TRUE.equals(p.isDefault())) makeDefault(s);
        return SignatoryView.of(s);
    }

    @Transactional
    public SignatoryView update(UUID id, SignatoryPayload p) {
        LetterSignatory s = require(id);
        if (p.name() != null) s.setName(required(p.name(), "Enter the signatory's name.", 120));
        if (p.title() != null) s.setTitle(optional(p.title(), 120));
        if (p.active() != null) s.setActive(p.active());
        if (Boolean.TRUE.equals(p.isDefault())) {
            if (!s.isActive()) throw invalid("Switch this signatory on before making them the default.");
            makeDefault(s);
        }
        signatories.save(s);
        return SignatoryView.of(s);
    }

    @Transactional
    public SignatoryView uploadImage(UUID id, MultipartFile file) {
        LetterSignatory s = require(id);
        s.setImage(cleaned(file), "image/png");
        signatories.save(s);
        return SignatoryView.of(s);
    }

    @Transactional
    public SignatoryView removeImage(UUID id) {
        LetterSignatory s = require(id);
        s.setImage(null, null);
        signatories.save(s);
        return SignatoryView.of(s);
    }

    @Transactional(readOnly = true)
    public Optional<StoredImage> image(UUID id) {
        return signatories.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .filter(LetterSignatory::hasImage)
                .map(s -> new StoredImage(s.getImage(), s.getImageType(), s.getUpdatedAt().toEpochMilli()));
    }

    // ---- the seal ----

    @Transactional
    public Instant uploadSeal(MultipartFile file) {
        Letterhead l = letterhead();
        l.setSeal(cleaned(file), "image/png");
        letterheads.save(l);
        return l.getUpdatedAt();
    }

    @Transactional
    public Instant removeSeal() {
        Letterhead l = letterhead();
        l.setSeal(null, null);
        letterheads.save(l);
        return l.getUpdatedAt();
    }

    @Transactional(readOnly = true)
    public Optional<StoredImage> seal() {
        return letterheads.findById(TenantContext.getCompanyId())
                .filter(l -> l.getSealType() != null)
                .map(l -> new StoredImage(l.getSealImage(), l.getSealType(), l.getUpdatedAt().toEpochMilli()));
    }

    // ---- choosing and placing ----

    /** Who signs a letter from this template: its own signatory if still active, else the default. */
    @Transactional(readOnly = true)
    public Optional<LetterSignatory> forTemplate(DocumentTemplate t) {
        UUID companyId = TenantContext.getCompanyId();
        if (t != null && t.getSignatoryId() != null) {
            Optional<LetterSignatory> own = signatories.findByIdAndCompanyId(t.getSignatoryId(), companyId)
                    .filter(LetterSignatory::isActive);
            if (own.isPresent()) return own;
        }
        return signatories.findFirstByCompanyIdAndActiveTrueAndIsDefaultTrue(companyId);
    }

    /** Whether the company has a seal to print. */
    @Transactional(readOnly = true)
    public boolean hasSeal() {
        return letterheads.findById(TenantContext.getCompanyId()).map(l -> l.getSealType() != null).orElse(false);
    }

    /** The signature and seal to draw on a letter issued under this signatory. */
    @Transactional(readOnly = true)
    public LetterPdf.Marks marksFor(UUID signatoryId) {
        byte[] seal = letterheads.findById(TenantContext.getCompanyId())
                .filter(l -> l.getSealType() != null).map(Letterhead::getSealImage).orElse(null);
        return new LetterPdf.Marks(imageBytes(signatoryId), seal);
    }

    /** Signature image bytes for a letter's signatory, or null. Inactive signatories still sign old letters. */
    @Transactional(readOnly = true)
    public byte[] imageBytes(UUID signatoryId) {
        if (signatoryId == null) return null;
        return signatories.findByIdAndCompanyId(signatoryId, TenantContext.getCompanyId())
                .filter(LetterSignatory::hasImage).map(LetterSignatory::getImage).orElse(null);
    }

    /**
     * Puts the signature marker on its own line just above the signatory's name — the last such line,
     * since the sign-off is at the end. Leaves the body alone when there is no image to draw, when the
     * name is not in the letter, or when the marker is already there.
     */
    static String withSignatureLine(String body, LetterSignatory signatory, boolean hasSeal) {
        if (body == null || signatory == null || (!signatory.hasImage() && !hasSeal) || body.contains(SIGNATURE_LINE)) {
            return body;
        }
        String[] lines = body.split("\n", -1);
        String want = signatory.getName().trim();
        for (int i = lines.length - 1; i >= 0; i--) {
            String text = lines[i].trim().replace("*", "").trim();
            if (text.equalsIgnoreCase(want)) {
                StringBuilder out = new StringBuilder();
                for (int j = 0; j < lines.length; j++) {
                    if (j == i) out.append(SIGNATURE_LINE).append('\n');
                    out.append(lines[j]);
                    if (j < lines.length - 1) out.append('\n');
                }
                return out.toString();
            }
        }
        return body;
    }

    private void makeDefault(LetterSignatory chosen) {
        for (LetterSignatory other : signatories.findByCompanyIdOrderByCreatedAtAsc(chosen.getCompanyId())) {
            if (!other.getId().equals(chosen.getId()) && other.isDefault()) {
                other.setDefault(false);
                signatories.save(other);
            }
        }
        chosen.setDefault(true);
        signatories.save(chosen);
    }

    private Letterhead letterhead() {
        UUID companyId = TenantContext.getCompanyId();
        return letterheads.findById(companyId).orElseGet(() -> letterheads.save(new Letterhead(companyId)));
    }

    private LetterSignatory require(UUID id) {
        return signatories.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Signatory not found"));
    }

    private static byte[] cleaned(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Choose an image to upload.");
        if (file.getSize() > SignatureImages.MAX_UPLOAD_BYTES) throw invalid("That image is larger than 3 MB.");
        try {
            return SignatureImages.clean(file.getBytes());
        } catch (IOException e) {
            throw invalid("That could not be read as an image. Upload a PNG or JPEG, or draw the signature instead.");
        }
    }

    private static String required(String v, String message, int max) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw invalid(message);
        if (t.length() > max) throw invalid("Keep it under " + max + " characters.");
        return t;
    }

    private static String optional(String v, int max) {
        String t = v == null ? "" : v.trim();
        if (t.length() > max) throw invalid("Keep it under " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
