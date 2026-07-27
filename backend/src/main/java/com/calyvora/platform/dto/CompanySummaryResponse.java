package com.calyvora.platform.dto;

/** One company as the platform owner sees it in the console. */
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
        String createdAt
) {
}
