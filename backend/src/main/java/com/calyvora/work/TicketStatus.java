package com.calyvora.work;

/** Lifecycle of a support ticket (Work OS slice S3; graduates to Service OS — SD-22b). */
public enum TicketStatus {
    OPEN,
    PENDING,
    RESOLVED,
    CLOSED
}
