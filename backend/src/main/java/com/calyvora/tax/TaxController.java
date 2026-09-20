package com.calyvora.tax;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Income tax: what an employee declares, and what it costs them.
 *
 * <p>Everything under {@code /me} is the caller's own and needs no role — a person's tax declaration
 * is their business, and gating it on HR would mean nobody could fill their own in. The two HR
 * routes are gated in {@link TaxService} against the reporting tree rather than here, for the same
 * reason the leave inbox is: a role expression cannot say "and only for their own company's people".
 *
 * <p>The financial year is a query parameter everywhere and defaults to the current one. It has to be
 * addressable — in March somebody is filing for the year that is ending and planning the next.
 */
@RestController
@RequestMapping("/api/v1/tax")
public class TaxController {

    private final TaxService taxService;

    public TaxController(TaxService taxService) {
        this.taxService = taxService;
    }

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

    /** The working: slabs, rebate, surcharge, cess, and what the next payslip should withhold. */
    @GetMapping("/me/computation")
    public TaxDtos.ComputationResponse myComputation(@CurrentUser AuthPrincipal principal,
                                                     @RequestParam(required = false) String year) {
        return taxService.myComputation(principal, year);
    }

    @GetMapping("/declarations")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<TaxDtos.DeclarationSummaryRow> all(@CurrentUser AuthPrincipal principal,
                                                   @RequestParam(required = false) String year) {
        return taxService.allDeclarations(principal, year);
    }

    /** HR opens the window in April and closes it before the last run of the year. */
    @PostMapping("/window")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public Map<String, Boolean> setWindow(@CurrentUser AuthPrincipal principal,
                                          @RequestBody Map<String, Boolean> body) {
        boolean open = body != null && Boolean.TRUE.equals(body.get("open"));
        return Map.of("open", taxService.setWindow(principal, open));
    }
}
