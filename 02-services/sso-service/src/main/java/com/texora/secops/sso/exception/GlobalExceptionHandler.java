package com.texora.secops.sso.exception;

import com.texora.secops.sso.dto.ErrorResponse;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** Central mapping of exceptions to the standard error body (shared standards B.7). */
@RestControllerAdvice
@Component("ssoGlobalExceptionHandler")
public class GlobalExceptionHandler {
	 private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(RbacDeniedException.class)
    public ResponseEntity<ErrorResponse> handleRbacDenied(RbacDeniedException e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "RBAC_DENIED", e.getMessage());
    }

    @ExceptionHandler(FailClosedException.class)
    public ResponseEntity<ErrorResponse> handleFailClosed(FailClosedException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // Fail closed: an unmapped exception is a 500, never treated as an
        // implicit ALLOW anywhere upstream of this handler.
        LOGGER.error("Unhandled exception on {} {}", "request", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, traceId != null ? traceId : "");
        return ResponseEntity.status(status).body(body);
    }
}
