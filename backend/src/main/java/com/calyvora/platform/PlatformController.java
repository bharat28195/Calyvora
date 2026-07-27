package com.calyvora.platform;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.platform.dto.CompanySummaryResponse;
import com.calyvora.platform.dto.CreateCompanyRequest;
import com.calyvora.platform.dto.SeatRequestResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Platform-owner (vendor) console — above all companies. OWNER only. Base {@code /api/v1/platform}. */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('OWNER')")
public class PlatformController {

    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/companies")
    public List<CompanySummaryResponse> companies(@CurrentUser AuthPrincipal principal) {
        return service.companies(principal.companyId());
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanySummaryResponse createCompany(@Valid @RequestBody CreateCompanyRequest req) {
        return service.createCompany(req);
    }

    @PostMapping("/companies/{id}/end")
    public CompanySummaryResponse endSubscription(@PathVariable UUID id) {
        return service.endSubscription(id);
    }

    @PostMapping("/companies/{id}/renew")
    public CompanySummaryResponse renew(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        return service.renewSubscription(id, body.getOrDefault("months", 12));
    }

    @PostMapping("/companies/{id}/seats")
    public CompanySummaryResponse setSeats(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        return service.setSeats(id, body.getOrDefault("seats", 5));
    }

    @GetMapping("/seat-requests")
    public List<SeatRequestResponse> seatRequests() {
        return service.pendingSeatRequests();
    }

    @PostMapping("/seat-requests/{id}/approve")
    public CompanySummaryResponse approve(@PathVariable UUID id) {
        return service.approveSeatRequest(id);
    }

    @PostMapping("/seat-requests/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID id) {
        service.declineSeatRequest(id);
    }
}
