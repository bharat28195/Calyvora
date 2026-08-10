package com.calyvora.platform.dto;

import java.math.BigDecimal;

/**
 * One agency as the platform owner sees it: who runs it, how many companies sit under it, and what
 * those companies are worth per month.
 *
 * <p>{@code monthlyRevenue} is the sum of its companies' bills — an agency workspace is never billed
 * itself, because it holds no employees. A company sold direct has no agency and simply doesn't appear
 * in any of these.
 */
public record AgencySummaryResponse(
        String agencyId,
        String name,
        String slug,
        String ownerName,
        String ownerEmail,
        int companyCount,
        long headcount,
        BigDecimal monthlyRevenue,
        String currency,
        String createdAt
) {
}
