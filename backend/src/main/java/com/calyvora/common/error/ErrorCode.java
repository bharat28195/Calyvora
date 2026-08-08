package com.calyvora.common.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical machine-readable error codes surfaced in the API error envelope (Sprint1 §13).
 * The frontend maps these to user-facing messages; keep them stable.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    /** The company's subscription has ended or lapsed — the workspace is locked until it is renewed. */
    SUBSCRIPTION_INACTIVE(HttpStatus.PAYMENT_REQUIRED),
    /** The action would take the company past the seats it is paying for. */
    SEAT_LIMIT_REACHED(HttpStatus.PAYMENT_REQUIRED),
    TOKEN_EXPIRED(HttpStatus.GONE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
