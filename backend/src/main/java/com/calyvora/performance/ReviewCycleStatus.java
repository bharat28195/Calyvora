package com.calyvora.performance;

/** Lifecycle of a review cycle. Closing it stops new edits from managers/members. */
public enum ReviewCycleStatus {
    OPEN,
    CLOSED
}
