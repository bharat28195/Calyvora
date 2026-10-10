package com.calyvora.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Owner/Admin opens a review cycle for a period. Dates are ISO (yyyy-MM-dd). */
public record CreateCycleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String periodStart,
        @NotBlank String periodEnd,
        /** The questions to ask; null or empty uses the standard set (PD-66). */
        java.util.List<com.calyvora.performance.ReviewForms.Question> questions
) {
    public CreateCycleRequest(String name, String periodStart, String periodEnd) {
        this(name, periodStart, periodEnd, null);
    }
}
