package com.calyvora.common.error;

import java.util.List;

/**
 * Base type for typed domain exceptions. Each carries an {@link ErrorCode} that the
 * {@link GlobalExceptionHandler} maps to an HTTP status and error envelope. Subclasses give call
 * sites an intention-revealing throw ({@code throw new ConflictException(...)}).
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode code;
    private final transient List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
