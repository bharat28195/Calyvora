package com.calyvora.people.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Update a goal — all fields optional. */
public record UpdateGoalRequest(
        @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Pattern(regexp = "OPEN|ACHIEVED|MISSED", message = "invalid status") String status,
        @Min(0) @Max(100) Integer progress,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "target date must be YYYY-MM-DD") String targetDate
) {}
