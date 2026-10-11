package com.calyvora.document;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Letters accepted with a signature (PD-72), from the three sides that touch them: HR sending and
 * following up, an employee signing inside Orbit, and a candidate signing through a link.
 */
@RestController
public class SigningController {

    private final SigningService signing;

    public SigningController(SigningService signing) {
        this.signing = signing;
    }

    // ---- HR ----

    @PostMapping("/api/v1/documents/{documentId}/signature")
    @PreAuthorize("@perm.has('DOCUMENTS_ISSUE')")
    public SigningService.RequestView send(@PathVariable UUID documentId,
                                           @RequestBody(required = false) SigningService.SendRequest req,
                                           @CurrentUser AuthPrincipal principal) {
        return signing.send(documentId, req, principal);
    }

    @GetMapping("/api/v1/documents/{documentId}/signature")
    @PreAuthorize("@perm.has('DOCUMENTS_ISSUE')")
    public List<SigningService.RequestView> requests(@PathVariable UUID documentId) {
        return signing.forDocument(documentId);
    }

    @PostMapping("/api/v1/documents/signature-requests/{requestId}/withdraw")
    @PreAuthorize("@perm.has('DOCUMENTS_ISSUE')")
    public SigningService.RequestView withdraw(@PathVariable UUID requestId) {
        return signing.withdraw(requestId);
    }

    @PostMapping("/api/v1/documents/signature-requests/{requestId}/remind")
    @PreAuthorize("@perm.has('DOCUMENTS_ISSUE')")
    public SigningService.RequestView remind(@PathVariable UUID requestId) {
        return signing.remind(requestId);
    }

    // ---- the employee: My letters ----

    @GetMapping("/api/v1/me/letters")
    public List<SigningService.RequestView> mine(@CurrentUser AuthPrincipal principal) {
        return signing.mine(principal);
    }

    @GetMapping("/api/v1/me/letters/{requestId}")
    public SigningService.SignerView open(@PathVariable UUID requestId, @CurrentUser AuthPrincipal principal) {
        return signing.openMine(requestId, principal);
    }

    @PostMapping("/api/v1/me/letters/{requestId}/accept")
    public SigningService.SignerView accept(@PathVariable UUID requestId, @RequestBody SigningService.AcceptRequest body,
                                            HttpServletRequest http, @CurrentUser AuthPrincipal principal) {
        return signing.acceptMine(requestId, body, ip(http), http.getHeader("User-Agent"), principal);
    }

    @PostMapping("/api/v1/me/letters/{requestId}/decline")
    public SigningService.SignerView decline(@PathVariable UUID requestId, @RequestBody SigningService.DeclineRequest body,
                                             HttpServletRequest http, @CurrentUser AuthPrincipal principal) {
        return signing.declineMine(requestId, body, ip(http), http.getHeader("User-Agent"), principal);
    }

    @GetMapping("/api/v1/me/letters/{requestId}/pdf")
    public ResponseEntity<byte[]> myPdf(@PathVariable UUID requestId, @CurrentUser AuthPrincipal principal) {
        return pdf(signing.myPdf(requestId, principal));
    }

    // ---- a candidate, through the link ----

    @GetMapping("/api/v1/public/sign/{token}")
    public SigningService.SignerView openLink(@PathVariable String token) {
        return signing.openByToken(token);
    }

    @PostMapping("/api/v1/public/sign/{token}/accept")
    public SigningService.SignerView acceptLink(@PathVariable String token, @RequestBody SigningService.AcceptRequest body,
                                                HttpServletRequest http) {
        return signing.acceptByToken(token, body, ip(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/api/v1/public/sign/{token}/decline")
    public SigningService.SignerView declineLink(@PathVariable String token, @RequestBody SigningService.DeclineRequest body,
                                                 HttpServletRequest http) {
        return signing.declineByToken(token, body, ip(http), http.getHeader("User-Agent"));
    }

    @GetMapping("/api/v1/public/sign/{token}/pdf")
    public ResponseEntity<byte[]> linkPdf(@PathVariable String token) {
        return pdf(signing.pdfByToken(token));
    }

    private static ResponseEntity<byte[]> pdf(DocumentDeliveryService.Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.bytes());
    }

    /** The signer's address as the world saw it: the first hop of X-Forwarded-For behind the host's proxy. */
    private static String ip(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
