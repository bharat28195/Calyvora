package com.calyvora.tax;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Income tax: what an employee declares (Form 124), the proofs behind it, HR's review, and what it
 * all costs (PD-55, PD-60).
 *
 * <p>Everything under {@code /me} is the caller's own and needs no role — a person's tax declaration
 * is their business. Everything else is HR's, gated on TAX_MANAGE here and again in the service.
 *
 * <p>The tax year is a query parameter everywhere and defaults to the current one. It has to be
 * addressable — in March somebody is finishing the year that is ending and planning the next.
 */
@RestController
@RequestMapping("/api/v1/tax")
public class TaxController {

    private final TaxService taxService;

    public TaxController(TaxService taxService) {
        this.taxService = taxService;
    }

    // ---- the employee's own ----------------------------------------------------------------------

    @GetMapping("/me/declaration")
    public TaxDtos.DeclarationResponse myDeclaration(@CurrentUser AuthPrincipal principal,
                                                     @RequestParam(required = false) String year) {
        return taxService.myDeclaration(principal, year);
    }

    @PutMapping("/me/declaration")
    public TaxDtos.DeclarationResponse save(@CurrentUser AuthPrincipal principal,
                                            @RequestParam(required = false) String year,
                                            @RequestBody TaxDtos.DeclarationPayload payload) {
        return taxService.save(principal, year, payload);
    }

    @PostMapping("/me/declaration/submit")
    public TaxDtos.DeclarationResponse submit(@CurrentUser AuthPrincipal principal,
                                              @RequestParam(required = false) String year) {
        return taxService.submit(principal, year);
    }

    /** The working: income, exemptions, deductions, slabs, rebate, surcharge, cess, the year by month. */
    @GetMapping("/me/computation")
    public TaxDtos.ComputationResponse myComputation(@CurrentUser AuthPrincipal principal,
                                                     @RequestParam(required = false) String year) {
        return taxService.myComputation(principal, year);
    }

    /** The same working for a form that has not been saved yet — the live figures while typing. */
    @PostMapping("/me/preview")
    public TaxDtos.ComputationResponse preview(@CurrentUser AuthPrincipal principal,
                                               @RequestParam(required = false) String year,
                                               @RequestBody TaxDtos.DeclarationPayload payload) {
        return taxService.preview(principal, year, payload);
    }

    @PostMapping(value = "/me/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaxDtos.DeclarationResponse uploadProof(@CurrentUser AuthPrincipal principal,
                                                   @RequestParam(required = false) String year,
                                                   @RequestParam String ownerType,
                                                   @RequestParam String ownerId,
                                                   @RequestParam("file") MultipartFile file) {
        return taxService.uploadProof(principal, year, ownerType, ownerId, file);
    }

    @DeleteMapping("/me/proofs/{proofId}")
    public TaxDtos.DeclarationResponse deleteProof(@CurrentUser AuthPrincipal principal,
                                                   @RequestParam(required = false) String year,
                                                   @PathVariable UUID proofId) {
        return taxService.deleteProof(principal, year, proofId);
    }

    @GetMapping("/me/form130")
    public TaxDtos.Form130Response myForm130(@CurrentUser AuthPrincipal principal,
                                             @RequestParam(required = false) String year) {
        return taxService.myForm130(principal, year);
    }

    /** A proof file — the employee's own, or anybody's for HR. */
    @GetMapping("/proofs/{proofId}")
    public ResponseEntity<byte[]> proof(@CurrentUser AuthPrincipal principal, @PathVariable UUID proofId) {
        TaxProof p = taxService.proof(principal, proofId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(p.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(p.getContent());
    }

    // ---- HR ---------------------------------------------------------------------------------------

    @GetMapping("/declarations")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public List<TaxDtos.DeclarationSummaryRow> all(@CurrentUser AuthPrincipal principal,
                                                   @RequestParam(required = false) String year) {
        return taxService.allDeclarations(principal, year);
    }

    @GetMapping("/declarations/{employeeId}")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.DeclarationResponse one(@CurrentUser AuthPrincipal principal, @PathVariable UUID employeeId,
                                           @RequestParam(required = false) String year) {
        return taxService.declarationOf(principal, employeeId, year);
    }

    @GetMapping("/declarations/{employeeId}/computation")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.ComputationResponse computation(@CurrentUser AuthPrincipal principal, @PathVariable UUID employeeId,
                                                   @RequestParam(required = false) String year) {
        return taxService.computationOf(principal, employeeId, year);
    }

    @PostMapping("/declarations/{employeeId}/review")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.DeclarationResponse review(@CurrentUser AuthPrincipal principal, @PathVariable UUID employeeId,
                                              @RequestParam(required = false) String year,
                                              @RequestBody TaxDtos.ReviewPayload payload) {
        return taxService.review(principal, employeeId, year, payload);
    }

    @PostMapping("/declarations/{employeeId}/reopen")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.DeclarationResponse reopen(@CurrentUser AuthPrincipal principal, @PathVariable UUID employeeId,
                                              @RequestParam(required = false) String year) {
        return taxService.reopen(principal, employeeId, year);
    }

    @GetMapping("/declarations/{employeeId}/form130")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.Form130Response form130(@CurrentUser AuthPrincipal principal, @PathVariable UUID employeeId,
                                           @RequestParam(required = false) String year) {
        return taxService.form130Of(principal, employeeId, year);
    }

    @GetMapping("/settings")
    public TaxDtos.TaxSettings settings() {
        return taxService.taxSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public TaxDtos.TaxSettings updateSettings(@CurrentUser AuthPrincipal principal,
                                              @RequestBody TaxDtos.TaxSettings body) {
        return taxService.updateTaxSettings(principal, body);
    }

    /** The older open/close switch for declarations. */
    @PostMapping("/window")
    @PreAuthorize("@perm.has('TAX_MANAGE')")
    public Map<String, Boolean> setWindow(@CurrentUser AuthPrincipal principal,
                                          @RequestBody Map<String, Boolean> body) {
        boolean open = body != null && Boolean.TRUE.equals(body.get("open"));
        return Map.of("open", taxService.setWindow(principal, open));
    }
}
