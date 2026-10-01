package com.calyvora.common.error;

/** Requested resource does not exist (or is invisible to the current tenant) → 404. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
