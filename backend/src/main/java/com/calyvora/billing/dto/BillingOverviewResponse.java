package com.calyvora.billing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A company's billing snapshot: the plan, what they owe this month for their current headcount, and a
 * recent invoice history (one line per month, priced on that month's headcount).
 */
public record BillingOverviewResponse(
        String plan,
        String status,
        BigDecimal pricePerEmployee,
        BigDecimal pricePerEmployeePerYear,
        String currency,
        String trialEndsAt,
        boolean trialActive,
        long billableEmployees,
        BigDecimal monthlyCharge,
        BigDecimal annualCharge,
        String currentMonth,
        String paidThrough,
        /**
         * The published volume tiers, so the UI can explain a bill that isn't simply headcount × one
         * rate. Null for a company on a negotiated flat rate, where there is nothing to explain.
         */
        List<PriceTier> tiers,
        List<Invoice> invoices
) {
    public record Invoice(String month, long headcount, BigDecimal amount, String status) {}

    /** "Employees 1–100 cost ₹149 each"; {@code toEmployee} is null on the final, open-ended tier. */
    public record PriceTier(long fromEmployee, Long toEmployee, BigDecimal rate) {}
}
