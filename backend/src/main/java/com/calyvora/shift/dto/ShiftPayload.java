package com.calyvora.shift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update a shift template. Times are ISO {@code HH:mm}. */
public record ShiftPayload(
        @NotBlank @Size(max = 60) String name,
        @NotBlank String startTime,
        @NotBlank String endTime,
        @Size(max = 16) String color
) {
}
