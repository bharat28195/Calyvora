package com.calyvora.performance;

/**
 * Where a single review is in its flow:
 * PENDING_SELF   → member still to write their self-assessment
 * PENDING_MANAGER→ self done (or skipped); manager to write the review + rating + hike
 * SUBMITTED      → manager submitted; awaiting Owner/Admin approval
 * APPROVED       → admin approved; any recommended hike has been applied to compensation
 * CLOSED         → finished with no raise applied
 */
public enum ReviewStatus {
    PENDING_SELF,
    PENDING_MANAGER,
    SUBMITTED,
    APPROVED,
    CLOSED
}
