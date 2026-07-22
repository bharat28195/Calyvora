package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mark one employee's day (feedback C.4). Owner/Admin only. */
public record MarkAttendanceRequest(
        @NotBlank String employeeId,
        /** ISO date; defaults to today when blank. */
        String date,
        @NotBlank @Pattern(regexp = "PRESENT|WORK_FROM_HOME|HALF_DAY|ABSENT|ON_LEAVE|HOLIDAY|WEEK_OFF",
                message = "invalid status") String status,
        /** ISO local times, e.g. "09:30". */
        String checkIn,
        String checkOut,
        @Size(max = 400) String note
) {}
