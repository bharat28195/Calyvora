package com.calyvora.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update a job opening. */
public record JobOpeningPayload(
        @NotBlank @Size(max = 140) String title,
        String departmentId,
        @Size(max = 120) String location,
        @Size(max = 24) String employmentType,
        String description,
        Integer positions,
        String status
) {
}
