package com.texora.secops.vpn.exception;

import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.vpn.dto.ErrorBody;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * VPN-specific exception mapping. The bean name MUST be unique: the starter already registers one called
 * "globalExceptionHandler" (KNOWN-INTEGRATION-PITFALLS #3). Do not delete this class to "fix" a collision.
 *
 * NOTE (see README "Unsure about"): the shared handler's own @Order is not known. This advice is pinned to
 * LOWEST_PRECEDENCE so that, if the shared one has a higher precedence, it wins for PlatformException subclasses.
 */
@RestControllerAdvice
@Component("vpnGlobalExceptionHandler")
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VpnException.class)
    public ResponseEntity<ErrorBody> handleVpn(VpnException e) {
        if (e.getStatus() >= 500) {
            LOGGER.error("VPN request failed code={} status={}", e.getCode(), e.getStatus(), e);
        } else {
            LOGGER.info("VPN request rejected code={} status={}", e.getCode(), e.getStatus());
        }
        return build(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<ErrorBody> handleUnauthenticated(PlatformException.UnauthenticatedException e) {
        LOGGER.info("Unauthenticated request rejected");
        return build(401, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorBody> handleValidation(Exception e) {
        return build(422, "VALIDATION_FAILED", "Request validation failed");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> handleMalformed(Exception e) {
        return build(400, "MALFORMED_REQUEST", "Malformed request");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethod(HttpRequestMethodNotSupportedException e) {
        return build(405, "METHOD_NOT_ALLOWED", "Method not allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMedia(HttpMediaTypeNotSupportedException e) {
        return build(415, "UNSUPPORTED_MEDIA_TYPE", "application/json only");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> handleNoResource(NoResourceFoundException e) {
        return build(404, "NOT_FOUND", "Not found");
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorBody> handleConflict(Exception e) {
        LOGGER.warn("Conflict: {}", e.getClass().getSimpleName());
        return build(409, "CONFLICT", "Conflicting concurrent change or duplicate");
    }

    /** Catch-all: ALWAYS logs the real exception, then returns a sanitized 500 (PITFALLS #3, #10). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        LOGGER.error("Unhandled exception", e);
        return build(500, "INTERNAL_ERROR", "Internal error");
    }

    private ResponseEntity<ErrorBody> build(int status, String code, String message) {
        ErrorBody body = new ErrorBody();
        body.setTimestamp(Instant.now().toString());
        body.setStatus(status);
        HttpStatus resolved = HttpStatus.resolve(status);
        body.setError(resolved != null ? resolved.getReasonPhrase() : "Error");
        body.setCode(code);
        body.setMessage(message);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.status(status).body(body);
    }
}
