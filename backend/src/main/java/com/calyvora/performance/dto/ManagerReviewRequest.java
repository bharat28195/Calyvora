package com.calyvora.performance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * The manager's side: a 1–5 rating, narrative, and a hike recommendation. All optional on a draft
 * save; {@code submit} flips the review to SUBMITTED for admin approval. {@code hikeType} is one of
 * PERCENT | NEW_SALARY | NONE — PERCENT reads {@code hikePercent}, NEW_SALARY reads {@code proposedSalary}.
 */
public record ManagerReviewRequest(
        @Min(1) @Max(5) Integer rating,
        String summary,
        String strengths,
        String improvements,
        String hikeType,
        BigDecimal hikePercent,
        BigDecimal proposedSalary,
        String hikeNote,
        boolean submit
) {
}
