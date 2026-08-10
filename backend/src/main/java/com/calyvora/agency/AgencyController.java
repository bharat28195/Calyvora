package com.calyvora.agency;

import com.calyvora.agency.dto.AgencyOverviewResponse;
import com.calyvora.agency.dto.AgencySeatRequest;
import com.calyvora.platform.dto.CompanySummaryResponse;
import com.calyvora.platform.dto.CreateCompanyRequest;
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
import java.util.UUID;

/**
 * The agency console (PD-18) — a customer's view of the several companies they run. Base
 * {@code /api/v1/agency}.
 *
 * <p>Deliberately smaller than the platform console. There is no endpoint here to activate, end,
 * renew or price a subscription, because selling is the vendor's to do: an agency provisions a
 * company and asks for seats, and the owner decides when billing starts.
 *
 * <p>Role <em>and</em> membership of an agency workspace, matching the platform console's two locks —
 * see {@link AgencyAccess}. Which companies are visible behind that door is enforced separately, per
 * call, in {@link AgencyService}.
 */
@RestController
@RequestMapping("/api/v1/agency")
@PreAuthorize("hasRole('AGENCY_OWNER') and @agencyAccess.granted()")
public class AgencyController {

    private final AgencyService service;

    public AgencyController(AgencyService service) {
        this.service = service;
    }

    /** Headline figures across every company this agency runs. */
    @GetMapping("/overview")
    public AgencyOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/companies")
    public List<CompanySummaryResponse> companies() {
        return service.companies();
    }

    @GetMapping("/companies/{id}")
    public CompanySummaryResponse company(@PathVariable UUID id) {
        return service.company(id);
    }

    /**
     * Add a company. It arrives locked, on a {@code PENDING} subscription, until the platform owner
     * activates it — so an agency can set a customer up in seconds without granting itself billing.
     */
    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanySummaryResponse createCompany(@Valid @RequestBody CreateCompanyRequest req) {
        return service.createCompany(req);
    }

    /** Ask the vendor for more seats; lands in the same queue a company admin's request does. */
    @PostMapping("/companies/{id}/request-seats")
    public CompanySummaryResponse requestSeats(@PathVariable UUID id,
                                               @Valid @RequestBody AgencySeatRequest req) {
        return service.requestSeats(id, req.seats(), req.note());
    }
}
