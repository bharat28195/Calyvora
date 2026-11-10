package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Full and final settlement (PD-73). Payroll's job, because it is salary data and money out. */
@RestController
@RequestMapping("/api/v1/people/employees/{employeeId}/settlement")
@PreAuthorize("@perm.has('PAYROLL_MANAGE')")
public class SettlementController {

    private final SettlementService settlements;

    public SettlementController(SettlementService settlements) {
        this.settlements = settlements;
    }

    @GetMapping
    public SettlementService.View get(@PathVariable UUID employeeId) {
        return settlements.get(employeeId);
    }

    @PatchMapping
    public SettlementService.View update(@PathVariable UUID employeeId, @RequestBody SettlementService.Payload payload) {
        return settlements.update(employeeId, payload);
    }

    @PostMapping("/approve")
    public SettlementService.View approve(@PathVariable UUID employeeId, @CurrentUser AuthPrincipal principal) {
        return settlements.approve(employeeId, principal);
    }

    @PostMapping("/reopen")
    public SettlementService.View reopen(@PathVariable UUID employeeId) {
        return settlements.reopen(employeeId);
    }

    @PostMapping("/paid")
    public SettlementService.View paid(@PathVariable UUID employeeId,
                                       @RequestBody(required = false) SettlementService.PaidRequest req) {
        return settlements.markPaid(employeeId, req);
    }
}
