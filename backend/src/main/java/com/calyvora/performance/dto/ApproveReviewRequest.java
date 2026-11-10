package com.calyvora.performance.dto;

/**
 * Approving a review (PD-66). {@code force} approves even though the employee never submitted their
 * self-assessment; {@code issueLetter} raises the increment or promotion letter in the same step.
 */
public record ApproveReviewRequest(Boolean force, Boolean issueLetter) {
    public static final ApproveReviewRequest DEFAULT = new ApproveReviewRequest(false, false);
}
