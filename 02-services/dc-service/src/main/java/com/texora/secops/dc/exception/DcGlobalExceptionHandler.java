package com.texora.secops.dc.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DC's own exception handler. Bean name is explicitly unique (pitfall #3): the shared starter registers a bean
 * named "globalExceptionHandler" and a duplicate name fails startup with BeanDefinitionOverrideException.
 *
 * <p>Order is LOWEST_PRECEDENCE so that, if the shared handler also matches an exception type, it is consulted
 * before this class's catch-all. See README (unverified assumption on shared handler ordering).
 */
@RestControllerAdvice
@Component("dcGlobalExceptionHandler")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DcGlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcGlobalExceptionHandler.class);

    /** Fields owned by HRMS (ADR D-2). Sending them to DC is a business-rule failure (422), not malformed JSON. */
    private static final Set<String> HRMS_OWNED_FIELDS = new HashSet<String>(
            Arrays.asList("username", "displayName", "orgUnitId", "status"));

    @ExceptionHandler(DcException.class)
    public ResponseEntity<ErrorResponse> handleDc(DcException e) {
        if (e.getStatus().is5xxServerError()) {
            LOGGER.error("DC upstream/server failure code={} message={}", e.getCode(), e.getMessage(), e);
        } else {
            LOGGER.warn("DC request rejected status={} code={} message={}", e.getStatus().value(), e.getCode(), e.getMessage());
        }
        return build(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map((FieldError fe) -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException) {
            String property = ((UnrecognizedPropertyException) cause).getPropertyName();
            if (HRMS_OWNED_FIELDS.contains(property)) {
                return build(HttpStatus.UNPROCESSABLE_ENTITY, "HRMS_OWNED_FIELD",
                        "Field '" + property + "' is owned by HRMS and cannot be authored in DC (ADR D-2)");
            }
            return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Unknown field '" + property + "'");
        }
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Invalid value for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        LOGGER.warn("Data integrity violation (treated as conflict): {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", "Request conflicts with existing data");
    }

    /**
     * Catch-all. MUST log the exception (pitfall #3/#10) or real defects become invisible.
     * Spring MVC's own exceptions (405, 404, 415 ...) implement ErrorResponse and keep their status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse) {
            HttpStatusCode status = ((org.springframework.web.ErrorResponse) e).getStatusCode();
            LOGGER.warn("MVC rejected request status={} type={}", status.value(), e.getClass().getSimpleName());
            return build(HttpStatus.valueOf(status.value()), "HTTP_" + status.value(), "Request could not be processed");
        }
        LOGGER.error("Unhandled exception - returning generic 500 (fail closed)", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An internal error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        ErrorResponse body = new ErrorResponse(Instant.now().toString(), status.value(), status.getReasonPhrase(),
                code, message, MDC.get("traceId"));
        return ResponseEntity.status(status).body(body);
    }
}
