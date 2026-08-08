package com.calyvora.platform;

import com.calyvora.billing.Subscription;
import com.calyvora.billing.SubscriptionRepository;
import com.calyvora.billing.SubscriptionStatus;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.util.Slugs;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.company.CompanyStatus;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.platform.dto.CompanySummaryResponse;
import com.calyvora.platform.dto.CreateCompanyRequest;
import com.calyvora.platform.dto.SeatRequestResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The platform-owner (vendor) layer (PD-10): one account above all companies that provisions tenants
 * and controls their subscriptions. This reads/writes across tenants — legitimate because companies,
 * users and subscriptions are not RLS-isolated (the auth surface + the now platform-managed
 * subscriptions table), so no RLS bypass is needed.
 */
@Service
public class PlatformService {

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("100");
    /** Sanity ceilings, so a slipped digit in the console can't be stored as a real commercial term. */
    private static final int MAX_SEATS = 100_000;
    private static final int MAX_RENEW_MONTHS = 120;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SeatRequestRepository seatRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.calyvora.billing.PricingService pricingService;

    public PlatformService(CompanyRepository companyRepository, CompanySettingsRepository settingsRepository,
                           UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                           SeatRequestRepository seatRequestRepository, PasswordEncoder passwordEncoder,
                           com.calyvora.billing.PricingService pricingService) {
        this.pricingService = pricingService;
        this.companyRepository = companyRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.seatRequestRepository = seatRequestRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---- companies ----

    /** Every customer company (excluding the owner's own platform company). */
    @Transactional(readOnly = true)
    public List<CompanySummaryResponse> companies(UUID platformCompanyId) {
        return companyRepository.findAll().stream()
                .filter(c -> !c.getId().equals(platformCompanyId))
                .map(this::summarize)
                .sorted(Comparator.comparing(CompanySummaryResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CompanySummaryResponse createCompany(CreateCompanyRequest req) {
        String email = req.adminEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("That email is already registered");
        }
        Company company = companyRepository.save(
                new Company(UUID.randomUUID(), req.companyName().trim(), uniqueSlug(req.companyName()),
                        CompanyStatus.ACTIVE));

        CompanySettings settings = new CompanySettings(company.getId());
        settings.setTimezone("Asia/Kolkata");
        settings.setCurrency("INR");
        settingsRepository.save(settings);

        User admin = new User(UUID.randomUUID(), company.getId(), email,
                req.adminFirstName().trim(), req.adminLastName().trim(), Role.ADMIN, UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);

        Subscription sub = new Subscription(UUID.randomUUID(), company.getId(), DEFAULT_PRICE, "INR", null);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartedAt(Instant.now());
        sub.setSeats(Math.max(1, req.seats()));
        sub.setEndsAt(LocalDate.now().plusMonths(Math.max(1, req.months())));
        subscriptionRepository.save(sub);

        return summarize(company);
    }

    /** Owner ends a company's subscription — its app locks immediately. */
    @Transactional
    public CompanySummaryResponse endSubscription(UUID companyId) {
        Subscription sub = requireSubscription(companyId);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        return summarize(requireCompany(companyId));
    }

    /** Reactivate (or extend) a subscription by N months from today (or its end date, whichever is later). */
    @Transactional
    public CompanySummaryResponse renewSubscription(UUID companyId, int months) {
        Subscription sub = requireSubscription(companyId);
        if (months < 1 || months > MAX_RENEW_MONTHS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Renewal must be between 1 and " + MAX_RENEW_MONTHS + " months",
                    fieldError("months", "must be between 1 and " + MAX_RENEW_MONTHS));
        }
        LocalDate base = sub.getEndsAt() != null && sub.getEndsAt().isAfter(LocalDate.now())
                ? sub.getEndsAt() : LocalDate.now();
        sub.setEndsAt(base.plusMonths(months));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        return summarize(requireCompany(companyId));
    }

    /**
     * Set the seat limit. Silently clamping an out-of-range value used to answer 200 while storing
     * something the owner never asked for — a mistyped "-5" became 1, quietly cutting the customer
     * off from seats they had paid for. Bad input is now refused and explained.
     */
    @Transactional
    public CompanySummaryResponse setSeats(UUID companyId, int seats) {
        Subscription sub = requireSubscription(companyId);
        if (seats < 1 || seats > MAX_SEATS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Seats must be between 1 and " + MAX_SEATS, fieldError("seats", "must be between 1 and " + MAX_SEATS));
        }
        long headcount = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        if (seats < headcount) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This company already has " + headcount + " active people — seats can't be set below that.",
                    fieldError("seats", "must be at least the current headcount (" + headcount + ")"));
        }
        sub.setSeats(seats);
        return summarize(requireCompany(companyId));
    }

    /** Set the subscription end date directly (editable in the console). A future date re-activates. */
    @Transactional
    public CompanySummaryResponse setEndDate(UUID companyId, LocalDate endsAt) {
        Subscription sub = requireSubscription(companyId);
        sub.setEndsAt(endsAt);
        if (endsAt != null && !endsAt.isBefore(LocalDate.now())) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
        }
        return summarize(requireCompany(companyId));
    }

    /**
     * Quote this company its own flat rate, overriding the published volume tiers.
     *
     * <p>Setting a price marks the subscription as custom, so a later change to the standard price
     * list can't silently overwrite what was agreed with this customer. Passing {@code null} puts
     * them back on the standard list.
     */
    @Transactional
    public CompanySummaryResponse setPrice(UUID companyId, BigDecimal pricePerEmployee) {
        Subscription sub = requireSubscription(companyId);
        if (pricePerEmployee == null) {
            sub.setCustomPrice(false);
        } else {
            // Silently dropping a negative price answered 200 while leaving the old rate in place, so
            // the console showed a change that had not happened.
            if (pricePerEmployee.signum() < 0 || pricePerEmployee.compareTo(MAX_PRICE) > 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Price must be between 0 and " + MAX_PRICE,
                        fieldError("price", "must be between 0 and " + MAX_PRICE));
            }
            sub.setPricePerEmployee(pricePerEmployee);
            sub.setCustomPrice(true);
        }
        return summarize(requireCompany(companyId));
    }

    // ---- pricing ----

    /** Every version of the price list, newest first, with the one currently in force flagged. */
    @Transactional(readOnly = true)
    public List<com.calyvora.platform.dto.PriceListResponse> priceLists() {
        UUID currentId = pricingService.current().getId();
        return pricingService.history().stream()
                .map(l -> com.calyvora.platform.dto.PriceListResponse.of(l, l.getId().equals(currentId)))
                .toList();
    }

    @Transactional
    public com.calyvora.platform.dto.PriceListResponse publishPriceList(
            com.calyvora.platform.dto.PublishPriceListRequest request) {
        LocalDate effectiveFrom;
        try {
            effectiveFrom = LocalDate.parse(request.effectiveFrom().trim());
        } catch (RuntimeException ex) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                    "Invalid start date — use YYYY-MM-DD");
        }
        List<com.calyvora.billing.PricingService.TierInput> tiers = request.tiers().stream()
                .map(t -> new com.calyvora.billing.PricingService.TierInput(t.toEmployee(), t.rate()))
                .toList();
        var saved = pricingService.publish(effectiveFrom, request.note(), tiers);
        return com.calyvora.platform.dto.PriceListResponse.of(
                saved, saved.getId().equals(pricingService.current().getId()));
    }

    // ---- seat requests ----

    @Transactional(readOnly = true)
    public List<SeatRequestResponse> pendingSeatRequests() {
        Map<UUID, String> names = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        return seatRequestRepository.findByStatusOrderByCreatedAtAsc(SeatRequestStatus.PENDING).stream()
                .map(r -> {
                    int current = subscriptionRepository.findByCompanyId(r.getCompanyId())
                            .map(Subscription::getSeats).orElse(0);
                    return new SeatRequestResponse(r.getId().toString(), r.getCompanyId().toString(),
                            names.getOrDefault(r.getCompanyId(), "—"), current, r.getRequestedSeats(),
                            r.getStatus().name(), r.getNote(), r.getCreatedAt().toString());
                })
                .toList();
    }

    @Transactional
    public CompanySummaryResponse approveSeatRequest(UUID requestId) {
        SeatRequest req = seatRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Seat request not found"));
        Subscription sub = requireSubscription(req.getCompanyId());
        sub.setSeats(req.getRequestedSeats());
        req.setStatus(SeatRequestStatus.APPROVED);
        req.setDecidedAt(Instant.now());
        return summarize(requireCompany(req.getCompanyId()));
    }

    @Transactional
    public void declineSeatRequest(UUID requestId) {
        SeatRequest req = seatRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Seat request not found"));
        req.setStatus(SeatRequestStatus.DECLINED);
        req.setDecidedAt(Instant.now());
    }

    // ---- helpers ----

    private static java.util.List<com.calyvora.common.error.ApiError.FieldError> fieldError(String field, String message) {
        return java.util.List.of(new com.calyvora.common.error.ApiError.FieldError(field, message));
    }

    private CompanySummaryResponse summarize(Company company) {
        long headcount = userRepository.countByCompanyIdAndStatus(company.getId(), UserStatus.ACTIVE);
        Subscription sub = subscriptionRepository.findByCompanyId(company.getId()).orElse(null);
        User admin = userRepository.findByCompanyIdOrderByCreatedAtAsc(company.getId()).stream()
                .filter(u -> u.getRole() == Role.ADMIN).findFirst()
                .orElseGet(() -> userRepository.findByCompanyIdOrderByCreatedAtAsc(company.getId())
                        .stream().findFirst().orElse(null));

        String endsAt = sub != null && sub.getEndsAt() != null ? sub.getEndsAt().toString() : null;
        Long daysLeft = sub != null && sub.getEndsAt() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), sub.getEndsAt()) : null;
        // The rate the next employee is charged at, and the actual bill. These use the same
        // calculation as the customer's own billing page — the owner console must never quote a
        // revenue figure the customer isn't being asked to pay.
        java.time.YearMonth thisMonth = java.time.YearMonth.now();
        BigDecimal price = sub == null ? null : pricingService.rateFor(sub, headcount, thisMonth);
        BigDecimal revenue = (sub == null || sub.isLocked())
                ? BigDecimal.ZERO : pricingService.monthlyFor(sub, headcount, thisMonth);
        return new CompanySummaryResponse(
                company.getId().toString(), company.getName(), company.getSlug(), company.getStatus().name(),
                admin == null ? "—" : (admin.getFirstName() + " " + admin.getLastName()).trim(),
                admin == null ? "—" : admin.getEmail(),
                headcount,
                sub == null ? 0 : sub.getSeats(),
                sub == null ? "NONE" : sub.getStatus().name(),
                endsAt, daysLeft,
                sub != null && sub.isLocked(),
                price, revenue, sub == null ? "INR" : sub.getCurrency(),
                company.getCreatedAt() == null ? null : company.getCreatedAt().toString());
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    private Subscription requireSubscription(UUID companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "No subscription for this company"));
    }

    private String uniqueSlug(String name) {
        String base = Slugs.slugify(name);
        String slug = base;
        int suffix = 1;
        while (companyRepository.existsBySlug(slug)) {
            slug = base + "-" + (++suffix);
        }
        return slug;
    }
}
