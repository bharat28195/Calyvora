package com.calyvora.dashboard.dto;

public record DashboardSummaryResponse(String companyName, long memberCount,
                                       long pendingInviteCount, String yourRole) {
}
