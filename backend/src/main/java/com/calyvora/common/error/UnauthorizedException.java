package com.calyvora.common.error;

/**
 * Authentication failed or is required → 401. Messages are intentionally generic to avoid user
 * enumeration (Sprint1 §10).
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
