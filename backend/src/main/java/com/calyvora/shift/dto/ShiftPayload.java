package com.calyvora.shift.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update a shift template. Times are ISO {@code HH:mm}. {@code workMinutes} is how much
 * actual work the shift expects (breaks excluded); omitted means nine hours on create and
 * "unchanged" on update.
 */
public record ShiftPayload(
        @NotBlank @Size(max = 60) String name,
        @NotBlank String startTime,
        @NotBlank String endTime,
        @Size(max = 16) String color,
        @Min(30) @Max(1440) Integer workMinutes
) {
}
