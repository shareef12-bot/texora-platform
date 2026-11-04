package com.texora.secops.siem.web;

import com.texora.secops.siem.dto.common.ErrorResponse;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.security.PolicyDeniedException;
import com.texora.secops.starter.exception.TenantContextMissingException;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Central mapping from exceptions to the standard error body (B.7). The default branch for any
 * unrecognized exception is a 500 with no internal detail leaked - fail closed, never let an
 * exception path be interpreted as success (B.6.3).
 *
 * <p><b>Unique bean name required</b> (KNOWN-INTEGRATION-PITFALLS.md item 3):
 * {@code texora-secops-starter}'s {@code StarterAutoConfiguration} registers its own shared
 * handler as a bean literally named {@code globalExceptionHandler}. This service needs its own
 * handler too - for {@code CrossTenantAccessException}, {@code PolicyDeniedException}, and the
 * other SIEM-specific exceptions the shared handler cannot know about - so it is explicitly named
 * {@code "siemGlobalExceptionHandler"} below to avoid a {@code BeanDefinitionOverrideException}
 * at startup. Per that same pitfalls item: do not delete this class to "fix" the collision - it
 * genuinely handles exceptions the shared one does not.
 *
 * <p>Also handles {@link TenantContextMissingException} and
 * {@code PlatformException.UnauthenticatedException} defensively: {@code RbacInterceptor} already
 * catches the latter locally and never lets it reach a controller in normal operation, but a
 * handler here is cheap insurance if either is ever thrown from a code path this service adds
 * later.
 */
@RestControllerAdvice
@Component("siemGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ErrorResponse> handleTenantContextMissing(TenantContextMissingException ex, HttpServletRequest req) {
        // Should not normally be reachable - RbacInterceptor populates CallerContext for every
        // annotated handler before it runs - but fails closed (401) rather than 500 if it is.
        LOGGER.error("Tenant context missing where one was required: {}", ex.getMessage());
        return body(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Valid SSO-issued bearer token required", req);
    }

    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(
            PlatformException.UnauthenticatedException ex, HttpServletRequest req) {
        // Defensive only - RbacInterceptor already catches this directly on the
        // TokenValidator.validate(...) call site and never lets it reach a controller in normal
        // operation. This exists in case a future code path calls TokenValidator elsewhere.
        return body(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Valid SSO-issued bearer token required", req);
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ErrorResponse> handleCrossTenant(CrossTenantAccessException ex, HttpServletRequest req) {
        LOGGER.warn("Cross-tenant access attempt blocked: {}", ex.getMessage());
        return body(HttpStatus.FORBIDDEN, "RBAC_DENIED", "Caller lacks required role", req);
    }

    @ExceptionHandler(PolicyDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePolicyDenied(PolicyDeniedException ex, HttpServletRequest req) {
        LOGGER.warn("Policy check denied: {}", ex.getMessage());
        return body(HttpStatus.FORBIDDEN, "POLICY_DENIED", ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), req);
    }

    @ExceptionHandler(UpstreamTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamTimeout(UpstreamTimeoutException ex, HttpServletRequest req) {
        LOGGER.error("Upstream dependency timeout: {}", ex.getMessage());
        return body(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "Upstream dependency did not respond in time", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request failed validation", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Fail closed on the unknown case too: no stack trace or internal detail in the body.
        LOGGER.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An internal error occurred", req);
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message, HttpServletRequest req) {
        String traceId = String.valueOf(req.getAttribute("traceId"));
        ErrorResponse response = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, message, traceId);
        return ResponseEntity.status(status).body(response);
    }
}
