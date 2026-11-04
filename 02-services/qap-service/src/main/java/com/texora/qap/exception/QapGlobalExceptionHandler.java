package com.texora.qap.exception;

import com.texora.qap.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for the QAP service.
 * Bean name is explicitly "qapGlobalExceptionHandler" to avoid conflicts
 * with the shared-library GlobalExceptionHandler.
 */
@RestControllerAdvice(basePackages = "com.texora.qap")
@org.springframework.stereotype.Component("qapGlobalExceptionHandler")
public class QapGlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QapGlobalExceptionHandler.class);

    @ExceptionHandler(SuiteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSuiteNotFound(SuiteNotFoundException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, "QAP_SUITE_NOT_FOUND", ex.getMessage(), request));
    }

    @ExceptionHandler(CredentialNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCredentialNotFound(CredentialNotFoundException ex,
                                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, "QAP_CREDENTIAL_NOT_FOUND", ex.getMessage(), request));
    }

    @ExceptionHandler(CredentialResolutionException.class)
    public ResponseEntity<ErrorResponse> handleCredentialResolution(CredentialResolutionException ex,
                                                                      HttpServletRequest request) {
        LOGGER.error("Credential resolution failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(buildError(HttpStatus.BAD_GATEWAY, "QAP_CREDENTIAL_RESOLUTION_FAILED", ex.getMessage(), request));
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ErrorResponse> handleCrossTenant(CrossTenantAccessException ex,
                                                            HttpServletRequest request) {
        LOGGER.warn("Cross-tenant access attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, "QAP_CROSS_TENANT_DENIED", ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "QAP_VALIDATION_ERROR", details, request));
    }

    /**
     * Catch-all handler. MUST log the exception per platform standards.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled exception on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "QAP_INTERNAL_ERROR",
                        "An unexpected error occurred", request));
    }

    private ErrorResponse buildError(HttpStatus status, String code, String message,
                                      HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                (String) request.getAttribute("traceId")
        );
    }
}
