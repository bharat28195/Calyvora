package com.calyvora.platform.dto;

/** A company's own view of its subscription (admin read-only + the app-lock check). */
public record SubscriptionView(
        String status,
        int seats,
        long seatsUsed,
        String endsAt,
        Long daysLeft,
        boolean locked,
        Integer pendingRequestSeats,
        java.math.BigDecimal pricePerEmployee,
        String currency
) {
}
