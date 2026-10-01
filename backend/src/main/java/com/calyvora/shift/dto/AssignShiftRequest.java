package com.calyvora.shift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Roster one employee onto one shift for one day.
 *
 * <p>Replaces a raw {@code Map<String,String>} that was read straight into {@code UUID.fromString} and
 * {@code LocalDate.parse} — a missing or malformed value threw and surfaced as {@code 500}, which
 * looked like a server fault rather than a bad request.
 */
public record AssignShiftRequest(
        @NotBlank(message = "is required")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a valid id")
        String employeeId,

        @NotBlank(message = "is required")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "must be a date in YYYY-MM-DD form")
        String onDate,

        @NotBlank(message = "is required")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a valid id")
        String shiftId
) {
}
