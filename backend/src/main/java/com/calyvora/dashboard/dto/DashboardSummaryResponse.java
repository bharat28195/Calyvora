package com.calyvora.dashboard.dto;

/**
 * The dashboard's company summary. Tenant-scoped. The Work counts (projects, tasks, the active
 * sprint) went with the work tracker, which is archived on branch archive/work-tracker-and-clients.
 */
public record DashboardSummaryResponse(
        String companyName,
        String yourRole,
        // People
        long memberCount,
        long pendingInviteCount,
        long departmentCount,
        // Company documents
        long spaceCount,
        long pageCount
) {
}
