package com.calyvora.shift.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The company's standard working day, for anyone not rostered onto a shift, and the absence rule
 * (V70): nobody checked in by {@code workDayStart} (or their shift's start) plus
 * {@code absentGraceMinutes} is absent for the day. Times are ISO {@code HH:mm}.
 */
public record WorkDayPayload(
        @NotNull @Min(30) @Max(1440) Integer workDayMinutes,
        @NotBlank String workDayStart,
        @NotNull @Min(0) @Max(720) Integer absentGraceMinutes
) {
}
