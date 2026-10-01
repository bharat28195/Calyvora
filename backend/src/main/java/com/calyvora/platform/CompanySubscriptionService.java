package com.calyvora.platform;

import com.calyvora.billing.Subscription;
import com.calyvora.billing.SubscriptionRepository;
import com.calyvora.common.error.ConflictException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.UserStatus;
import com.calyvora.identity.UserRepository;
import com.calyvora.platform.dto.RequestSeatsRequest;
import com.calyvora.platform.dto.SubscriptionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A company's own window onto its subscription: the admin reads status/seats/end-date (they can no
 * longer manage billing — PD-10 pt 6) and can request more seats, which lands in the owner's queue.
 */
@Service
public class CompanySubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SeatRequestRepository seatRequestRepository;
    private final UserRepository userRepository;
    private final com.calyvora.billing.PricingService pricingService;

    public CompanySubscriptionService(SubscriptionRepository subscriptionRepository,
                                      SeatRequestRepository seatRequestRepository,
                                      UserRepository userRepository,
                                      com.calyvora.billing.PricingService pricingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.seatRequestRepository = seatRequestRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public SubscriptionView mine() {
        UUID companyId = TenantContext.getCompanyId();
        long seatsUsed = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        Subscription sub = subscriptionRepository.findByCompanyId(companyId).orElse(null);
        Integer pending = seatRequestRepository
                .findByCompanyIdAndStatus(companyId, SeatRequestStatus.PENDING)
                .map(SeatRequest::getRequestedSeats).orElse(null);

        if (sub == null) {
            // No subscription row (e.g. the platform company itself) — treat as open, never locked.
            return new SubscriptionView("NONE", 0, seatsUsed, null, null, false, pending, null, null, "INR");
        }
        Long daysLeft = sub.getEndsAt() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), sub.getEndsAt());
        // The rate the next hire is charged at, and the bill that rate actually produces. Both come
        // from PricingService so this page and the owner console can never quote different numbers;
        // a cancelled subscription is charged nothing, matching the console's revenue line.
        java.time.YearMonth thisMonth = java.time.YearMonth.now();
        java.math.BigDecimal rate = pricingService.rateFor(sub, seatsUsed, thisMonth);
        java.math.BigDecimal monthlyCharge = sub.isLocked()
                ? java.math.BigDecimal.ZERO : pricingService.monthlyFor(sub, seatsUsed, thisMonth);
        return new SubscriptionView(sub.getStatus().name(), sub.getSeats(), seatsUsed,
                sub.getEndsAt() == null ? null : sub.getEndsAt().toString(), daysLeft, sub.isLocked(),
                pending, rate, monthlyCharge, sub.getCurrency());
    }

    @Transactional
    public SubscriptionView requestSeats(RequestSeatsRequest req) {
        UUID companyId = TenantContext.getCompanyId();
        seatRequestRepository.findByCompanyIdAndStatus(companyId, SeatRequestStatus.PENDING)
                .ifPresent(r -> { throw new ConflictException("A seat request is already pending"); });
        seatRequestRepository.save(new SeatRequest(UUID.randomUUID(), companyId, req.seats(),
                req.note() == null || req.note().isBlank() ? null : req.note().trim()));
        return mine();
    }
}
