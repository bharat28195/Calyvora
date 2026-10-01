package com.calyvora.dashboard.dto;

import java.util.List;

/**
 * Owner/Admin team overview (founder feedback B1–B5, C.4).
 *
 * <p>Attendance now comes from the real daily record where one exists (C.4): a marked row wins, and
 * an unmarked day still falls back to approved leave (the phase-1 behaviour). People nobody has
 * marked are counted as in — but {@code unmarkedToday} says how many that is, so the owner can see
 * how much of {@code presentToday} is an assumption rather than a record. {@code monthLeaves} feeds
 * the leave calendar.
 */
public record TeamOverviewResponse(
        long headcount,
        long presentToday,
        long onLeaveToday,
        long unmarkedToday,
        List<LeaveToday> outToday,
        List<CalendarLeave> monthLeaves
) {
    /** Someone out today, with the reason so the owner sees why. */
    public record LeaveToday(String employeeName, String type, String reason, String startDate, String endDate) {}

    /** A leave overlapping the current month, for the calendar grid. */
    public record CalendarLeave(String employeeName, String type, String status, String startDate, String endDate) {}
}
