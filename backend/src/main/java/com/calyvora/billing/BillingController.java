package com.calyvora.billing;

import com.calyvora.billing.dto.BillingOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company's own subscription &amp; billing view. Base {@code /api/v1/billing}. Tenant-scoped: it
 * reports the bill for the caller's own company, so it is only meaningful to that company's admin.
 *
 * <p><b>Open question (QA 2026-08-09):</b> PD-10 pt 3 removed billing management from admins in favour
 * of the read-only {@code /subscription/me} view, but this endpoint still exposes pricing and invoice
 * actions to ADMIN. It is currently unreachable from the admin navigation. Restricting it to OWNER is
 * not the fix — the owner belongs to the platform company, so OWNER-only would return the vendor's own
 * bill instead. Either the endpoint is retired or admins keep it deliberately; that is a product call.
 */
@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @GetMapping
    public BillingOverviewResponse overview() {
        return service.overview();
    }

    @PostMapping("/activate")
    public BillingOverviewResponse activate() {
        return service.activate();
    }

    @PostMapping("/invoices/{month}/pay")
    public BillingOverviewResponse pay(@PathVariable String month) {
        return service.pay(month);
    }
}
