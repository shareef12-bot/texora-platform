package com.texora.secops.ldap.exception;

import com.texora.secops.ldap.sync.client.LdapEngineException;
import com.texora.secops.starter.exception.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service-specific exception handler for ldap-service.
 *
 * <p>Bean name {@code ldapGlobalExceptionHandler} avoids collision with the
 * {@code globalExceptionHandler} bean registered by {@code texora-secops-starter}
 * (see KNOWN-INTEGRATION-PITFALLS §3).
 */
@RestControllerAdvice
@Component("ldapGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SelfApprovalException.class)
    public ResponseEntity<Map<String, Object>> handleSelfApproval(SelfApprovalException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_APPROVAL_DENIED", ex.getMessage());
    }

    @ExceptionHandler(LdapEngineException.class)
    public ResponseEntity<Map<String, Object>> handleLdapEngine(LdapEngineException ex) {
        LOGGER.error("LDAP engine error: {}", ex.getMessage(), ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNREACHABLE",
                "LDAP engine is unreachable or returned an error — operation denied");
    }

    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(PlatformException.UnauthenticatedException ex) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Valid bearer token required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    /** Catch-all — must log the real exception so nothing is silently swallowed. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        LOGGER.error("Unhandled exception in ldap-service", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        body.put("traceId", MDC.get("traceId") != null ? MDC.get("traceId") : "unknown");
        return ResponseEntity.status(status).body(body);
    }
}
