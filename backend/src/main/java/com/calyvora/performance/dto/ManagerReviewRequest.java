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
        boolean submit,
        /** Answers to the cycle's questions for the manager (PD-66). */
        java.util.Map<String, com.calyvora.performance.ReviewForms.Answer> answers,
        /** A promotion: the new job title, applied on approval. Blank for none. */
        String newTitle,
        /** When the hike and title take effect (yyyy-MM-dd); today when blank. */
        String effectiveDate
) {
    public ManagerReviewRequest(Integer rating, String summary, String strengths, String improvements, String hikeType,
                                java.math.BigDecimal hikePercent, java.math.BigDecimal proposedSalary, String hikeNote,
                                boolean submit) {
        this(rating, summary, strengths, improvements, hikeType, hikePercent, proposedSalary, hikeNote, submit, null, null, null);
    }
}
