package com.calyvora.team.dto;

import java.util.List;

/**
 * The team roster and its headline counts.
 *
 * @param month        the month the attendance figures cover, so the page cannot label a stale figure
 *                     as "this month" after midnight on the 1st.
 * @param directCount  people reporting straight to the viewer.
 * @param totalCount   the whole subtree beneath them.
 */
public record TeamSummaryResponse(
        String month,
        int directCount,
        int totalCount,
        long presentToday,
        long onLeaveToday,
        long pendingLeaveRequests,
        long openExpenseClaims,
        List<TeamMemberResponse> members
) {
}
