package com.calyvora.platform;

import com.calyvora.platform.dto.RequestSeatsRequest;
import com.calyvora.platform.dto.SubscriptionView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company's own subscription window. Base {@code /api/v1/subscription}. */
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final CompanySubscriptionService service;

    public SubscriptionController(CompanySubscriptionService service) {
        this.service = service;
    }

    /** Any signed-in company user can read it — the app uses it to lock when the subscription ends. */
    @GetMapping("/me")
    public SubscriptionView mine() {
        return service.mine();
    }

    /** Admins ask the owner for more seats; the request lands in the owner's console queue. */
    @PostMapping("/request-seats")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubscriptionView requestSeats(@Valid @RequestBody RequestSeatsRequest req) {
        return service.requestSeats(req);
    }
}
