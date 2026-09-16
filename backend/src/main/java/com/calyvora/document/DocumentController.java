package com.calyvora.document;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.document.dto.DocumentResponse;
import com.calyvora.document.dto.GenerateRequest;
import com.calyvora.document.dto.LetterheadPayload;
import com.calyvora.document.dto.LetterheadResponse;
import com.calyvora.document.dto.PreviewResponse;
import com.calyvora.document.dto.TemplatePayload;
import com.calyvora.document.dto.TemplateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Templates + generated letters (feedback D2 ⭐ / D3). Owner/Admin only — these documents carry
 * salary and exit details, so the whole surface is role-gated rather than per-endpoint.
 */
@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class DocumentController {

    private final DocumentService documentService;
    private final LetterheadService letterheadService;

    public DocumentController(DocumentService documentService, LetterheadService letterheadService) {
        this.documentService = documentService;
        this.letterheadService = letterheadService;
    }

    // ---- letterhead ----

    /**
     * The company letterpad. Always answers — a company that has never configured one gets the
     * defaults with its own name, so there is no "not set up yet" state to handle in the client.
     */
    @GetMapping("/letterhead")
    public LetterheadResponse letterhead() {
        return letterheadService.get();
    }

    @PatchMapping("/letterhead")
    public LetterheadResponse updateLetterhead(@Valid @RequestBody LetterheadPayload payload) {
        return letterheadService.update(payload);
    }

    /**
     * Upload the company's own letterpad, to print letters on as-is.
     *
     * <p>Owner/Admin only, and deliberately narrower than the rest of this controller: the
     * stationery every letter the company issues goes out on is not something anyone who can write
     * a letter should be able to replace.
     */
    @PostMapping("/letterhead/background")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public LetterheadResponse uploadLetterpad(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return letterheadService.uploadBackground(file);
    }

    @DeleteMapping("/letterhead/background")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public LetterheadResponse removeLetterpad() {
        return letterheadService.removeBackground();
    }

    /**
     * The uploaded letterpad itself. Readable by anyone who may read a letter, because every letter
     * is printed on it; the tenant binding is what keeps it to this company.
     */
    @GetMapping("/letterhead/background")
    public org.springframework.http.ResponseEntity<byte[]> letterpadImage() {
        return letterheadService.background()
                .map(image -> org.springframework.http.ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(image.contentType()))
                        // Private: it is one company's stationery, and a shared cache must not hand
                        // it to another. Revalidated by the version in the URL the client asks for.
                        .cacheControl(org.springframework.http.CacheControl
                                .maxAge(java.time.Duration.ofDays(30)).cachePrivate())
                        .body(image.bytes()))
                .orElseGet(() -> org.springframework.http.ResponseEntity.notFound().build());
    }

    // ---- templates ----

    @GetMapping("/templates")
    public List<TemplateResponse> templates(@CurrentUser AuthPrincipal principal) {
        return documentService.listTemplates(principal);
    }

    @GetMapping("/templates/{templateId}")
    public TemplateResponse template(@PathVariable UUID templateId) {
        return documentService.template(templateId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse createTemplate(@Valid @RequestBody TemplatePayload payload,
                                           @CurrentUser AuthPrincipal principal) {
        return documentService.createTemplate(payload, principal);
    }

    @PatchMapping("/templates/{templateId}")
    public TemplateResponse updateTemplate(@PathVariable UUID templateId,
                                           @Valid @RequestBody TemplatePayload payload) {
        return documentService.updateTemplate(templateId, payload);
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable UUID templateId) {
        documentService.deleteTemplate(templateId);
    }

    /** The merge fields available to template authors. */
    @GetMapping("/fields")
    public List<MergeFields.Field> fields() {
        return documentService.fieldCatalogue();
    }

    // ---- generation ----

    @PostMapping("/preview")
    public PreviewResponse preview(@Valid @RequestBody GenerateRequest request,
                                   @CurrentUser AuthPrincipal principal) {
        return documentService.preview(request, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse generate(@Valid @RequestBody GenerateRequest request,
                                     @CurrentUser AuthPrincipal principal) {
        return documentService.generate(request, principal);
    }

    @GetMapping
    public List<DocumentResponse> documents(@RequestParam(required = false) UUID employeeId) {
        return documentService.listDocuments(employeeId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse document(@PathVariable UUID documentId) {
        return documentService.document(documentId);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
    }
}
