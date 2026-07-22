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
    ANNOUNCEMENT,
}
