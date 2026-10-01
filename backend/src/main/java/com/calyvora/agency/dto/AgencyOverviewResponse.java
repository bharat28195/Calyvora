package com.calyvora.agency.dto;

import java.math.BigDecimal;

/**
 * The agency's own headline figures, across every company it runs.
 *
 * <p>{@code monthlySpend} is what the agency is billed, not what its companies earn — the same
 * number the platform owner sees as revenue, read from the other side of the invoice.
 */
public record AgencyOverviewResponse(
        String agencyName,
        int companies,
        long headcount,
        long seats,
        int lockedCompanies,
        BigDecimal monthlySpend,
        String currency
) {
}
