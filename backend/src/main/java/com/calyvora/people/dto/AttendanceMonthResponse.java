package com.calyvora.people.dto;

import java.util.List;
import java.util.Map;

/**
 * One employee's month: every day resolved, plus a summary.
 *
 * <p>{@code attendanceRate} is worked days over expected days, where <b>expected means every working
 * day of the month that has already passed</b> — holidays and week-offs excluded, half days counting
 * as 0.5. A working day with nothing recorded on it counts towards expected and not towards worked,
 * which is the whole point of the measure: somebody who turned up three times in a month has not had
 * a perfect month, and reading the rate off only the days that happen to have a row said they had.
 *
 * <p>{@code notRecorded} is how many of those expected days have no record at all. It is reported
 * separately so the screen can explain its own denominator: without it a rate of 21% next to three
 * green days and nothing else is a number the reader cannot reconcile.
 *
 * <p>Today is deliberately excluded while it has no record — the day is not over, and counting it as
 * missed would show everybody a dip every morning that repaired itself when they checked in.
 */
public record AttendanceMonthResponse(
        String employeeId,
        String employeeName,
        String month,
        List<AttendanceEntryResponse> days,
        Map<String, Long> counts,
        double workedDays,
        long expectedDays,
        /** Expected days with no record at all — the gap between worked and expected. */
        long notRecorded,
        Double attendanceRate
) {}
