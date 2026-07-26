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
        List<Invoice> invoices
) {
    public record Invoice(String month, long headcount, BigDecimal amount, String status) {}
}
