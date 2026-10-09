package com.calyvora.people.dto;

import java.util.List;

/**
 * One employee's one day. {@code derived} means nobody marked this day — the status was inferred
 * (approved leave, or a weekend); {@code status} is null when the day is simply unmarked.
 *
 * <p>{@code checkIn} is the first check-in and {@code checkOut} the last check-out. {@code sessions}
 * are the individual in/out pairs (V70): a lunch break is the gap between two of them.
 * {@code grossMinutes} runs from first in to last out; {@code effectiveMinutes} adds up only the
 * closed sessions; {@code openSince} is set while someone is checked in, so a screen can count the
 * running session itself. {@code requiredMinutes} comes from the shift they are rostered on that day,
 * else the company's standard day.
 */
public record AttendanceEntryResponse(
        String employeeId,
        String employeeName,
        String jobTitle,
        String department,
        String date,
        String status,
        String checkIn,
        String checkOut,
        String note,
        boolean derived,
        List<Session> sessions,
        Integer grossMinutes,
        Integer effectiveMinutes,
        String openSince,
        Integer requiredMinutes
) {
    /** One check-in/check-out pair; {@code checkOut} is null while it is still running. */
    public record Session(String checkIn, String checkOut) {}
}
