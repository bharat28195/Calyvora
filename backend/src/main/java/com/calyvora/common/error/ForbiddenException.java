package com.calyvora.common.error;

/** Authenticated but not permitted (role or tenant boundary) → 403. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
