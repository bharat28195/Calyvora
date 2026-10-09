package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A manager or HR sets several people's days at once — no approval step, they hold the right (V70).
 * Every listed person gets every listed date. Times are optional ISO {@code HH:mm}.
 */
public record CorrectAttendanceRequest(
        @NotEmpty @Size(max = 500) List<String> employeeIds,
        @NotEmpty @Size(max = 62) List<String> dates,
        @NotBlank @Pattern(regexp = "PRESENT|WORK_FROM_HOME|HALF_DAY|ABSENT|ON_LEAVE",
                message = "invalid status") String status,
        String checkIn,
        String checkOut,
        @NotBlank(message = "Give a reason for the correction") @Size(max = 400) String reason
) {}
