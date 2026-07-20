package com.calyvora.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSprintRequest(
        @NotBlank @Size(min = 1, max = 120) String name,
        @Size(max = 500) String goal,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "start date must be YYYY-MM-DD") String startDate,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "end date must be YYYY-MM-DD") String endDate
) {
}
