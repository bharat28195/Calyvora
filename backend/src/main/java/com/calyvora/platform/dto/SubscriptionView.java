package com.calyvora.platform.dto;

/**
 * A company's own view of its subscription (admin read-only + the app-lock check).
 *
 * <p>{@code monthlyCharge} is what the company is actually billed this month, computed by the same
 * {@code PricingService} the owner console quotes from — an admin who can see the rate but not the
 * bill can't answer "what are we spending on this?", and two screens deriving the number separately
 * is how they end up disagreeing.
 */
public record SubscriptionView(
        String status,
        int seats,
        long seatsUsed,
        String endsAt,
        Long daysLeft,
        boolean locked,
        Integer pendingRequestSeats,
        java.math.BigDecimal pricePerEmployee,
        java.math.BigDecimal monthlyCharge,
        String currency
) {
}
