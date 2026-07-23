package com.calyvora.notification;

/** What happened. Drives the icon and grouping in the inbox. */
public enum NotificationType {
    /** Someone asked for leave and you're the approver. */
    LEAVE_REQUESTED,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    /** Your manager set you a goal. */
    GOAL_ASSIGNED,
    /** A letter was issued for you. */
    DOCUMENT_ISSUED,
    /** A review cycle opened and your self-assessment is requested. */
    REVIEW_STARTED,
    /** Your report submitted their self-assessment; your review is due. */
    REVIEW_SELF_SUBMITTED,
    /** A manager submitted a review for your approval. */
    REVIEW_SUBMITTED,
    /** Your review was approved (and any hike applied). */
    REVIEW_APPROVED,
    ANNOUNCEMENT,
}
