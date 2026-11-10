package com.calyvora.payroll;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Finalise and reopen payroll months — payroll managers only. */
@RestController
@RequestMapping("/api/v1/payroll/months")
@PreAuthorize("@perm.has('PAYROLL_MANAGE')")
public class PayrollLockController {

    private final PayrollLockService service;

    public PayrollLockController(PayrollLockService service) {
        this.service = service;
    }

    @GetMapping
    public List<PayrollLockService.MonthStatus> finalized() {
        return service.finalizedMonths();
    }

    @GetMapping("/{month}")
    public PayrollLockService.MonthStatus status(@PathVariable String month) {
        return service.status(month);
    }

    @PostMapping("/{month}/finalize")
    public PayrollLockService.MonthStatus finalizeMonth(@PathVariable String month,
                                                        @CurrentUser AuthPrincipal principal) {
        return service.finalizeMonth(month, principal);
    }

    @PostMapping("/{month}/reopen")
    public PayrollLockService.MonthStatus reopen(@PathVariable String month) {
        return service.reopen(month);
    }
}
