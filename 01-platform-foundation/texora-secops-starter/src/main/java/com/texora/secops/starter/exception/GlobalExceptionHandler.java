package com.texora.secops.starter.exception;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Platform-wide exception handler.
 *
 * <p>Converts every {@link PlatformException} subclass (and a handful of Spring
 * framework exceptions) into the standard {@link ApiError} body (standard B.7).
 *
 * <p>All unexpected exceptions are caught by {@link #handleUnexpected}, logged
 * at ERROR level with the trace ID, and returned as 500 Internal Server Error
 * with no internal detail leaked to the caller.
 *
 * <p>Registered via Spring Boot auto-configuration in
 * {@link com.texora.secops.starter.config.StarterAutoConfiguration}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Optional<Tracer> tracer;

    public GlobalExceptionHandler(Optional<Tracer> tracer) {
        this.tracer = tracer;
    }

    // -------------------------------------------------------------------------
    // Platform exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(PlatformException.BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            PlatformException.BadRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(
            PlatformException.UnauthenticatedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
            PlatformException.ForbiddenException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            PlatformException.NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            PlatformException.ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.UnprocessableException.class)
    public ResponseEntity<ApiError> handleUnprocessable(
            PlatformException.UnprocessableException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PlatformException.UpstreamTimeoutException.class)
    public ResponseEntity<ApiError> handleUpstreamTimeout(
            PlatformException.UpstreamTimeoutException ex, HttpServletRequest request) {
        LOGGER.warn("Upstream timeout [code={}]: {}", ex.getCode(), ex.getMessage(), ex);
        return error(HttpStatus.GATEWAY_TIMEOUT, ex.getCode(), ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // Spring / bean-validation exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String detail = ex.getBindingResult()
            .getAllErrors()
            .stream()
            .map(e -> {
                if (e instanceof FieldError fe) {
                    return fe.getField() + ": " + fe.getDefaultMessage();
                }
                return e.getDefaultMessage();
            })
            .collect(Collectors.joining("; "));

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not readable");
    }

    // -------------------------------------------------------------------------
    // Tenant context failures (treat as 401 — unauthenticated path reached repo)
    // -------------------------------------------------------------------------

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ApiError> handleTenantMissing(
            TenantContextMissingException ex, HttpServletRequest request) {
        LOGGER.error("TenantContext missing — authentication filter may not be registered: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "MISSING_TENANT_CONTEXT",
            "Request could not be authenticated");
    }

    // -------------------------------------------------------------------------
    // Catch-all — fail closed, log everything
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        String traceId = resolveTraceId();
        LOGGER.error("Unexpected error [traceId={}] on {} {}: {}",
            traceId, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        // Never leak internal detail — fail closed
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(
                Instant.now(),
                500,
                "Internal Server Error",
                "INTERNAL_ERROR",
                "An unexpected error occurred. Contact platform support with traceId: " + traceId,
                traceId
            ));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            resolveTraceId()
        );
        return ResponseEntity.status(status).body(body);
    }

    private String resolveTraceId() {
        return tracer
            .map(Tracer::currentSpan)
            .filter(span -> span != null && span.context() != null)
            .map(span -> span.context().traceId())
            .orElse("unavailable");
    }
}
