package com.calyvora.people.dto;

import java.util.List;
import java.util.Map;

/**
 * One employee's month: every day resolved, plus a summary. {@code attendanceRate} is worked days
 * over expected days (holidays and week-offs excluded, half days count as 0.5), or null when the
 * month has nothing to measure yet.
 */
public record AttendanceMonthResponse(
        String employeeId,
        String employeeName,
        String month,
        List<AttendanceEntryResponse> days,
        Map<String, Long> counts,
        double workedDays,
        long expectedDays,
        Double attendanceRate
) {}
