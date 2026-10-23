package com.texora.secops.sec.config;

import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.starter.exception.TenantContextMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * SEC's own exception handler — returns the standard error body per B.7.
 *
 * <p><strong>Why this class exists alongside the shared one:</strong> SEC services throw
 * {@code ResponseStatusException} and rely on Spring Security's {@code AccessDeniedException} /
 * {@code AuthenticationException}, none of which the shared handler in
 * {@code texora-secops-starter} knows about. Do not delete it (see KNOWN-INTEGRATION-PITFALLS 3).</p>
 *
 * <p><strong>Bean name:</strong> the starter registers a bean literally named
 * {@code globalExceptionHandler}; without an explicit, unique name this class collides with it at
 * startup ({@code BeanDefinitionOverrideException}). Pattern: {@code {service}GlobalExceptionHandler}.</p>
 *
 * <p>Because this advice contains a catch-all {@code Exception} handler, it can shadow the shared
 * advice for shared exception types. The two shared types SEC actually produces
 * ({@code TenantContextMissingException}, {@code PlatformException.UnauthenticatedException}) are
 * therefore mapped explicitly below, so they keep their intended 403 / 401 and never degrade into
 * a 500.</p>
 */
@RestControllerAdvice
@Component("secGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return errorBody(status, status.name().replace("_", ""), ex.getReason());
    }

    /**
     * No tenant could be established for the request. Same outcome as the former local
     * TenantContext (authenticated caller, unknown tenant = authorization failure): 403, never an
     * empty result (B.5).
     */
    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<Map<String, Object>> handleTenantMissing(TenantContextMissingException ex) {
        LOGGER.warn("Tenant context missing — denying request");
        return errorBody(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_MISSING",
                "Tenant context not established — missing tenant claim in token");
    }

    /**
     * Thrown by the shared TokenValidator on ANY token failure (fail closed) -> 401.
     */
    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(
            PlatformException.UnauthenticatedException ex) {
        LOGGER.warn("Unauthenticated request rejected: {}", ex.getMessage());
        return errorBody(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Valid bearer token required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return errorBody(HttpStatus.FORBIDDEN, "RBAC_DENIED", "Caller lacks required role");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return errorBody(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Valid bearer token required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return errorBody(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    /**
     * Catch-all. MUST log the exception (with stack trace) before returning the sanitized body,
     * otherwise real errors are invisible (KNOWN-INTEGRATION-PITFALLS 3 and 10). Fails closed: any
     * unexpected error is a 500, never interpreted as success.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        LOGGER.error("Unhandled exception", ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<Map<String, Object>> errorBody(HttpStatus status, String code, String message) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "code", code,
                "message", message != null ? message : "",
                "traceId", UUID.randomUUID().toString()
        );
        return ResponseEntity.status(status).body(body);
    }
}
