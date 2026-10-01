package com.calyvora.people.dto;

/**
 * One employee's one day. {@code derived} means nobody marked this day — the status was inferred
 * (approved leave, or a weekend); {@code status} is null when the day is simply unmarked.
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
        boolean derived
) {}
