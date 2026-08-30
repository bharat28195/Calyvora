package com.calyvora.billing;

import com.calyvora.billing.dto.BillingOverviewResponse;
import com.calyvora.billing.dto.BillingOverviewResponse.Invoice;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.UserStatus;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subscription billing: per active employee, per month. The monthly charge is
 * {@code pricePerEmployee × active headcount}, so growing from 5 to 20 people means being billed for
 * 20 the month you have 20. Invoice history is derived from employee start dates, so it's real from
 * day one. Seat counting is a cheap {@code count} query, so this scales to thousands of employees.
 */
@Service
public class BillingService {

    /** The platform's default price: ₹100 per employee per month (₹1,200/employee/year). */
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("100");
    private static final String DEFAULT_CURRENCY = "INR";
    private static final int INVOICE_MONTHS = 6;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PricingService pricingService;

    public BillingService(SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                          EmployeeRepository employeeRepository, PricingService pricingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public BillingOverviewResponse overview() {
        UUID companyId = TenantContext.getCompanyId();
        Subscription sub = getOrCreate(companyId);

        long billable = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        YearMonth now = YearMonth.now();
        // Graduated by headcount from the price list in force this month, unless this company was
        // quoted its own flat rate.
        BigDecimal rate = pricingService.rateFor(sub, billable, now);
        BigDecimal monthly = pricingService.monthlyFor(sub, billable, now);
        BigDecimal annual = monthly.multiply(BigDecimal.valueOf(12));
        BigDecimal prepaid = pricingService.annualPrepaidFor(sub, billable, now);

        return new BillingOverviewResponse(
                sub.getPlan(), sub.getStatus().name(), rate, rate.multiply(BigDecimal.valueOf(12)),
                sub.getCurrency(),
                sub.getTrialEndsAt() == null ? null : sub.getTrialEndsAt().toString(),
                sub.getStatus() == SubscriptionStatus.TRIALING,
                billable, monthly, annual,
                now.toString(), sub.getPaidThrough(),
                sub.isCustomPrice() ? null : tierBreakdown(now, PricingService.currencyOf(sub)),
                sub.isCustomPrice() ? null : pricingService.listFor(now, PricingService.currencyOf(sub)).getMonthlyMinimum(),
                pricingService.minimumApplies(sub, billable, now),
                prepaid, annual.subtract(prepaid),
                invoices(companyId, sub, now));
    }

    @Transactional
    public BillingOverviewResponse activate() {
        UUID companyId = TenantContext.getCompanyId();
        Subscription sub = getOrCreate(companyId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        if (sub.getStartedAt() == null) sub.setStartedAt(java.time.Instant.now());
        return overview();
    }

    /** Settle a month's invoice (demo billing — no real payment gateway). */
    @Transactional
    public BillingOverviewResponse pay(String month) {
        UUID companyId = TenantContext.getCompanyId();
        Subscription sub = getOrCreate(companyId);
        YearMonth m;
        try {
            m = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid month (expected YYYY-MM)");
        }
        if (m.isAfter(YearMonth.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Can't pay a future month");
        }
        // Advance the paid-through watermark (you settle oldest→newest).
        if (sub.getPaidThrough() == null || m.isAfter(YearMonth.parse(sub.getPaidThrough()))) {
            sub.setPaidThrough(m.toString());
        }
        if (sub.getStatus() == SubscriptionStatus.TRIALING || sub.getStatus() == SubscriptionStatus.PAST_DUE) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            if (sub.getStartedAt() == null) sub.setStartedAt(java.time.Instant.now());
        }
        return overview();
    }

    private Subscription getOrCreate(UUID companyId) {
        return subscriptionRepository.findByCompanyId(companyId).orElseGet(() ->
                subscriptionRepository.save(new Subscription(UUID.randomUUID(), companyId,
                        DEFAULT_PRICE, DEFAULT_CURRENCY, java.time.Instant.now().plus(14, ChronoUnit.DAYS))));
    }

    /** The price list in force this month, so the UI can explain a bill that isn't headcount × one rate. */
    private List<BillingOverviewResponse.PriceTier> tierBreakdown(YearMonth month, String currency) {
        List<BillingOverviewResponse.PriceTier> out = new ArrayList<>();
        long from = 1;
        for (PriceListTier tier : pricingService.tiersFor(month, currency)) {
            Long upTo = tier.getUpTo() == null ? null : tier.getUpTo().longValue();
            out.add(new BillingOverviewResponse.PriceTier(from, upTo, tier.getRate()));
            if (upTo == null) {
                break;
            }
            from = upTo + 1;
        }
        return out;
    }

    /** One invoice per recent month, headcount derived from who had started by the end of that month. */
    private List<Invoice> invoices(UUID companyId, Subscription sub, YearMonth now) {
        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        YearMonth paidThrough = sub.getPaidThrough() == null ? null : YearMonth.parse(sub.getPaidThrough());

        List<Invoice> out = new ArrayList<>();
        for (int i = INVOICE_MONTHS - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();
            long headcount = employees.stream()
                    .filter(e -> e.getStartDate() != null && !e.getStartDate().isAfter(monthEnd))
                    .count();
            // Priced on that month's headcount *and* that month's price list, so an old invoice keeps
            // reading what the customer was actually asked to pay after a price change.
            BigDecimal amount = pricingService.monthlyFor(sub, headcount, ym);
            String status;
            if (paidThrough != null && !ym.isAfter(paidThrough)) {
                status = "PAID";
            } else if (ym.isBefore(now)) {
                status = "OVERDUE";
            } else {
                status = "DUE";
            }
            out.add(new Invoice(ym.toString(), headcount, amount, status));
        }
        return out;
    }
}
