package com.calyvora.platform.dto;

/**
 * One company as a console sees it. Shared by the platform owner's view and an agency's, which is
 * safe because it is deliberately company-level only — headcount and money, never an employee.
 *
 * <p>{@code agencyId}/{@code agencyName} are null for a company sold direct, which is how the owner
 * console tells the two kinds apart: direct customers stand alone, agency ones group underneath.
 *
 * <p>{@code customPrice} says where {@code pricePerEmployee} came from — a rate agreed with this
 * customer, or the published list. Without it the console shows a number with no way to tell whether
 * publishing a new price list would move it, which is exactly the question you ask before publishing
 * one.
 */
public record CompanySummaryResponse(
        String companyId,
        String name,
        String slug,
        String status,
        String adminName,
        String adminEmail,
        long headcount,
        int seats,
        String subscriptionStatus,
        String endsAt,
        Long daysLeft,
        boolean locked,
        java.math.BigDecimal pricePerEmployee,
        boolean customPrice,
        java.math.BigDecimal monthlyRevenue,
        String currency,
        String createdAt,
        String agencyId,
        String agencyName
) {
}
