package com.calyvora.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single API error envelope (Sprint1 §13). Never contains stack traces, secrets, or raw tokens.
 *
 * <pre>
 * { "timestamp": "...", "status": 400, "code": "VALIDATION_ERROR",
 *   "message": "Human message", "correlationId": "uuid",
 *   "errors": [ { "field": "email", "message": "already in use" } ] }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(ErrorCode code, String message, String correlationId, List<FieldError> errors) {
        return new ApiError(
                Instant.now(),
                code.status().value(),
                code.name(),
                message,
                correlationId,
                (errors == null || errors.isEmpty()) ? null : errors
        );
    }
}
