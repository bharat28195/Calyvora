package com.calyvora.team.dto;

import java.math.BigDecimal;

/**
 * One person on the "My team" roster: enough to see how they are doing this month without opening
 * their profile, and deliberately no pay.
 *
 * <p>There is no salary, CTC or payslip field here and there must never be one. A lead needs to know
 * that somebody was absent nine days; they do not need to know what the company pays them, and the one
 * screen built for leads is exactly where that would leak to every lead at once.
 *
 * @param direct whether they report to the viewer directly — the rest of the subtree is shown too, and
 *               the two look different on the page because "my report" and "somebody in my org" are
 *               different relationships even though both are visible.
 */
public record TeamMemberResponse(
        String employeeId,
        String userId,
        String name,
        String jobTitle,
        String department,
        String managerName,
        boolean direct,
        int depth,
        String employmentStatus,
        String todayStatus,
        long presentDays,
        long absentDays,
        long leaveDays,
        long pendingLeaveRequests,
        long openExpenseClaims,
        BigDecimal openExpenseAmount,
        Integer rating,
        String reviewStatus
) {
}
