package com.calyvora.billing;

import com.calyvora.billing.dto.BillingOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscription &amp; billing, platform-owner only. Base {@code /api/v1/billing}.
 *
 * <p>Company admins deliberately have no billing surface (PD-10 pt 3): they see the end date and can
 * request more seats, and the vendor does the charging. ADMIN used to be allowed here, which exposed
 * pricing and let a customer's own admin act on invoices.
 */
@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('OWNER')")
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
