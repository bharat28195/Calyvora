package com.calyvora.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Owner/Admin opens a review cycle for a period. Dates are ISO (yyyy-MM-dd). */
public record CreateCycleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String periodStart,
        @NotBlank String periodEnd
) {
}
