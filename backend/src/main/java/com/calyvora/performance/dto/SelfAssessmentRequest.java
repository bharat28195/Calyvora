package com.calyvora.performance.dto;

/** The member's own write-up of what they did this period. Empty is allowed until they submit. */
public record SelfAssessmentRequest(
        String selfAssessment,
        /** When true, mark self-assessment submitted and hand the review to the manager. */
        boolean submit
) {
}
