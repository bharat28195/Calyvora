package com.calyvora.dashboard.dto;

/**
 * Cross-app "command center" summary for the dashboard — one call that reaches into People, Work,
 * and Knowledge so the landing page proves the platform is one system, not three. Tenant-scoped.
 */
public record DashboardSummaryResponse(
        String companyName,
        String yourRole,
        // People
        long memberCount,
        long pendingInviteCount,
        long departmentCount,
        // Work
        long projectCount,
        long openTaskCount,
        long doneTaskCount,
        long openTicketCount,
        // Knowledge
        long spaceCount,
        long pageCount,
        // The active sprint's progress, if one is running (else null)
        ActiveSprint activeSprint
) {
    public record ActiveSprint(String name, long total, long done) {}
}
