package com.calyvora.common.error;

/** Request conflicts with current state (e.g. duplicate email, already a member) → 409. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
