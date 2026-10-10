package com.calyvora.tax;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The challans TDS was paid on and the 24Q receipt numbers — what Form 130 Part A lists. HR only. */
@RestController
@RequestMapping("/api/v1/tax/deposits")
@PreAuthorize("@perm.has('TAX_MANAGE')")
public class TdsDepositController {

    private final TdsDepositService service;

    public TdsDepositController(TdsDepositService service) {
        this.service = service;
    }

    @GetMapping
    public TaxDtos.DepositsResponse get(@CurrentUser AuthPrincipal principal,
                                        @RequestParam(required = false) String year) {
        return service.deposits(principal, year);
    }

    @PutMapping("/challans/{month}")
    public TaxDtos.DepositsResponse saveChallan(@CurrentUser AuthPrincipal principal, @PathVariable String month,
                                                @RequestBody TaxDtos.ChallanPayload body) {
        return service.saveChallan(principal, month, body);
    }

    @DeleteMapping("/challans/{month}")
    public TaxDtos.DepositsResponse clearChallan(@CurrentUser AuthPrincipal principal, @PathVariable String month) {
        return service.clearChallan(principal, month);
    }

    @PutMapping("/receipts/{quarter}")
    public TaxDtos.DepositsResponse saveReceipt(@CurrentUser AuthPrincipal principal, @PathVariable String quarter,
                                                @RequestBody TaxDtos.ReceiptPayload body) {
        return service.saveReceipt(principal, quarter, body);
    }
}
