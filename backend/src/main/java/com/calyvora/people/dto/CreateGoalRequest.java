package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create a goal for an employee. */
public record CreateGoalRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "target date must be YYYY-MM-DD") String targetDate
) {}
