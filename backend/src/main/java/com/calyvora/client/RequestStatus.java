package com.calyvora.client;

/** Lifecycle of a client request/ask. */
public enum RequestStatus {
    REQUESTED,
    IN_PROGRESS,
    DELIVERED,
    DECLINED,
}
