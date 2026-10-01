package com.calyvora.agency;

import com.calyvora.agency.dto.AgencyOverviewResponse;
import com.calyvora.billing.Subscription;
import com.calyvora.billing.SubscriptionRepository;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.platform.PlatformService;
import com.calyvora.platform.SeatRequest;
import com.calyvora.platform.SeatRequestRepository;
import com.calyvora.platform.SeatRequestStatus;
import com.calyvora.platform.dto.CompanySummaryResponse;
import com.calyvora.platform.dto.CreateCompanyRequest;
import com.calyvora.common.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The agency console (PD-18): one customer's view of the several companies they run.
 *
 * <p><b>The invariant.</b> Every read and write starts from {@link #myAgencyId()} — the caller's own
 * bound tenant — and every company is fetched through {@link #requireMyCompany}, which 404s anything
 * whose {@code agency_id} isn't ours. No method here takes a company id on trust. An agency asking for
 * another agency's company gets the same answer as asking for one that doesn't exist.
 *
 * <p><b>Why summaries only.</b> RLS binds one {@code company_id} per connection, and this caller's is
 * its own workspace — so every RLS-protected table (employees, payroll, leave…) returns nothing for it
 * anyway. Reading companies/users/subscriptions, the three tables outside RLS, is the whole capability.
 * That is a deliberate ceiling, not an omission: an agency sees headcount and cost, never a person.
 */
@Service
public class AgencyService {

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SeatRequestRepository seatRequestRepository;
    private final PlatformService platformService;

    public AgencyService(CompanyRepository companyRepository,
                         SubscriptionRepository subscriptionRepository,
                         SeatRequestRepository seatRequestRepository,
                         PlatformService platformService) {
        this.companyRepository = companyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.seatRequestRepository = seatRequestRepository;
        this.platformService = platformService;
    }

    /** The companies under this agency, newest information first-hand from the same summariser. */
    @Transactional(readOnly = true)
    public List<CompanySummaryResponse> companies() {
        return companyRepository.findByAgencyIdOrderByNameAsc(myAgencyId()).stream()
                .map(platformService::summarize)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgencyOverviewResponse overview() {
        UUID agencyId = myAgencyId();
        Company workspace = companyRepository.findById(agencyId)
                .orElseThrow(() -> new NotFoundException("Agency not found"));
        List<CompanySummaryResponse> companies = companies();

        long headcount = 0;
        long seats = 0;
        int locked = 0;
        BigDecimal monthlySpend = BigDecimal.ZERO;
        for (CompanySummaryResponse c : companies) {
            headcount += c.headcount();
            seats += c.seats();
            if (c.locked()) locked++;
            monthlySpend = monthlySpend.add(
                    c.monthlyRevenue() == null ? BigDecimal.ZERO : c.monthlyRevenue());
        }
        return new AgencyOverviewResponse(workspace.getName(), companies.size(), headcount, seats,
                locked, monthlySpend, "INR");
    }

    /**
     * Add a company to this agency. It is created with a {@code PENDING} subscription and is therefore
     * locked: the agency provisions the workspace and its first admin, the vendor decides when billing
     * starts. That split is the whole reason this console exists separately from the owner's.
     */
    @Transactional
    public CompanySummaryResponse createCompany(CreateCompanyRequest req) {
        // The request's own agencyId is ignored on purpose — an agency can only ever create under
        // itself, so the id comes from the caller's tenant rather than from anything they can set.
        Company created = platformService.provision(req, myAgencyId(), false);
        return platformService.summarize(created);
    }

    /**
     * Ask the vendor for more seats on one of our companies. Reuses the same {@link SeatRequest} queue
     * a company admin's request lands in, so the owner has one place to approve seats from.
     */
    @Transactional
    public CompanySummaryResponse requestSeats(UUID companyId, int seats, String note) {
        Company company = requireMyCompany(companyId);
        seatRequestRepository.findByCompanyIdAndStatus(companyId, SeatRequestStatus.PENDING)
                .ifPresent(r -> {
                    throw new ConflictException("A seat request is already pending for " + company.getName());
                });
        seatRequestRepository.save(new SeatRequest(UUID.randomUUID(), companyId, seats,
                note == null || note.isBlank() ? null : note.trim()));
        return platformService.summarize(company);
    }

    @Transactional(readOnly = true)
    public CompanySummaryResponse company(UUID companyId) {
        return platformService.summarize(requireMyCompany(companyId));
    }

    // ---- the isolation boundary ----

    /** The caller's own agency workspace. Bound by {@code TenantFilter} from the authenticated token. */
    private UUID myAgencyId() {
        return TenantContext.getCompanyId();
    }

    /**
     * A company id is only ever accepted if it belongs to the caller's agency. Deliberately
     * {@code NotFound} rather than {@code Forbidden}: a 403 would confirm the company exists, which is
     * itself a leak across agencies.
     */
    private Company requireMyCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .filter(c -> myAgencyId().equals(c.getAgencyId()))
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    /** Unused today; kept so the subscription lookup has an obvious home when drill-down arrives. */
    Subscription subscriptionOf(UUID companyId) {
        requireMyCompany(companyId);
        return subscriptionRepository.findByCompanyId(companyId).orElse(null);
    }
}
