package com.calyvora.billing;

import com.calyvora.billing.dto.BillingOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Subscription & billing (Owner/Admin). Base {@code /api/v1/billing}. */
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
