package com.calyvora.common.error;

import com.calyvora.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;

/**
 * Translates every exception into the one {@link ApiError} envelope (Sprint1 §13). Fail-safe:
 * anything unexpected becomes a generic 500 with no stack trace, secret, or raw token leaked.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Typed domain exceptions carry their own error code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(), correlationId(request), ex.getFieldErrors());
        return ResponseEntity.status(ex.getCode().status()).body(body);
    }

    /** Bean Validation failures on request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR, "Validation failed", correlationId(request), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Spring Security authentication failure (thrown before controllers). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.UNAUTHORIZED, "Authentication required", correlationId(request), null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /** @PreAuthorize / method-security denial. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                correlationId(request), null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Anything uncaught: log with the correlation id, return an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("Unhandled exception [correlationId={}]", correlationId, ex);
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR, "Something went wrong", correlationId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(),
                Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid"));
    }

    private static String correlationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return attr != null ? attr.toString() : null;
    }
}
