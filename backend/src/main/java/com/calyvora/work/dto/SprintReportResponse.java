package com.calyvora.work.dto;

import java.util.List;

/**
 * Everything a sprint review asks about (V23): how much was committed, how much is done, whether the
 * team over-committed against its capacity, the day-by-day burndown, and who's carrying what.
 */
public record SprintReportResponse(
        String sprintId,
        String name,
        String goal,
        String status,
        String startDate,
        String endDate,
        Integer capacityPoints,
        int committedPoints,
        int completedPoints,
        int remainingPoints,
        int totalTasks,
        int doneTasks,
        /** Points still unestimated — a burndown lies if half the board has no numbers. */
        int unestimatedTasks,
        int daysTotal,
        int daysElapsed,
        List<BurndownPoint> burndown,
        List<MemberLoad> byAssignee
) {
    /** One day of the chart. {@code ideal} is the straight line from committed to zero. */
    public record BurndownPoint(String date, Integer remainingPoints, double ideal, boolean projected) {}

    /** What one person is carrying in this sprint. */
    public record MemberLoad(String employeeId, String name, int points, int tasks, int donePoints) {}
}
