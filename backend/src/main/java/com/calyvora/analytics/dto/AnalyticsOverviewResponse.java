package com.calyvora.analytics.dto;

import java.util.List;

/**
 * Company-wide analytics for the Insights dashboard (Owner/Admin). Every figure is computed from data
 * we actually hold — no synthetic history. Series are lists of {@link Slice} (label + value) so the
 * frontend can render each as a donut, bar, or trend without bespoke shapes per metric.
 */
public record AnalyticsOverviewResponse(People people, Work work, Finance finance) {

    /** A single labelled data point in a chart series. */
    public record Slice(String label, double value) {}

    public record People(
            long headcount,
            long newJoinersThisYear,
            double avgTenureMonths,
            long onLeaveToday,
            long goalsOpen,
            long goalsAchieved,
            long goalsMissed,
            double avgGoalProgress,
            List<Slice> byDepartment,
            List<Slice> headcountGrowth,   // cumulative headcount at the end of each of the last 12 months
            List<Slice> ratingDistribution, // how many people at each 1–5 rating
            List<Slice> leaveByType         // approved leave days this year, by type
    ) {}

    public record Work(
            long projects,
            List<Slice> tasksByStatus,
            List<Slice> tasksByPriority,
            List<Slice> ticketsByStatus,
            ActiveSprint activeSprint,
            List<Slice> velocity            // completed story points per finished sprint (most recent last)
    ) {
        public record ActiveSprint(String name, int committed, int done, int remaining, int unestimated) {}
    }

    public record Finance(
            String currency,
            double pending,
            double awaitingReimbursement,
            double reimbursedThisYear,
            List<Slice> byCategory
    ) {}
}
