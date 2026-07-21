package com.calyvora.dashboard.dto;

import java.util.List;

/**
 * Owner/Admin team overview (founder feedback B1–B5). Attendance is <em>derived</em> from approved
 * leave for now (phase 1): "on leave today" = an approved leave covering today, "present" = everyone
 * else. {@code monthLeaves} feeds the leave calendar. Full daily attendance comes later (Bucket C).
 */
public record TeamOverviewResponse(
        long headcount,
        long presentToday,
        long onLeaveToday,
        List<LeaveToday> outToday,
        List<CalendarLeave> monthLeaves
) {
    /** Someone out today, with the reason so the owner sees why. */
    public record LeaveToday(String employeeName, String type, String reason, String startDate, String endDate) {}

    /** A leave overlapping the current month, for the calendar grid. */
    public record CalendarLeave(String employeeName, String type, String status, String startDate, String endDate) {}
}
