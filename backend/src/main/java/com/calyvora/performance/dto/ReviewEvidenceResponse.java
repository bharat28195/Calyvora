package com.calyvora.performance.dto;

import com.calyvora.work.dto.ContributionResponse;

/**
 * The factual record behind one review (PD-22, D1) — what this person actually delivered during the
 * cycle's period, assembled from Work OS and attendance.
 *
 * <p>This is the feature no competitor can copy cheaply: because work, people and attendance share
 * one schema, the review opens pre-filled with evidence instead of asking a manager to remember six
 * months. It is deliberately <em>not</em> a score — nothing here feeds the rating automatically. A
 * manager judges; the evidence just stops them guessing.
 *
 * <p>Goals are not repeated here: {@link PerformanceReviewResponse} already carries them.
 */
public record ReviewEvidenceResponse(
        String reviewId,
        String employeeName,
        String periodStart,
        String periodEnd,
        ContributionResponse work,
        AttendanceSummary attendance,
        boolean empty) {

    /**
     * Presence over the period. Context for the conversation, never an input to the rating —
     * someone on approved leave has not underperformed.
     */
    public record AttendanceSummary(
            int daysRecorded,
            int daysPresent,
            int daysWorkFromHome,
            int daysHalfDay,
            int daysOnLeave,
            int daysAbsent) {

        public static AttendanceSummary empty() {
            return new AttendanceSummary(0, 0, 0, 0, 0, 0);
        }
    }
}
