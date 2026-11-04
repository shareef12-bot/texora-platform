package com.texora.secops.cgi.common;

import com.texora.secops.cgi.common.dto.ErrorBody;
import com.texora.secops.starter.exception.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Service-specific exception mapping. The explicit bean name avoids the BeanDefinitionOverrideException with
 * the starter's own {@code globalExceptionHandler} (KNOWN-INTEGRATION-PITFALLS #3).
 *
 * <p>Every response uses the B.7 error body and never echoes request data. The catch-all LOGS the exception
 * before returning the generic 500 so real faults are never swallowed.
 */
@RestControllerAdvice
@Component("cgiGlobalExceptionHandler")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CgiApiException.class)
    public ResponseEntity<ErrorBody> handleApi(CgiApiException e) {
        return build(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** The shared IAM client fails closed with this; map it to 401. */
    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<ErrorBody> handleUnauthenticated(PlatformException.UnauthenticatedException e) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Request validation failed");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ErrorBody> handleMalformed(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethod(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        LOGGER.warn("Data integrity violation mapped to 409: {}", e.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "CONFLICT", "Request conflicts with existing data");
    }

    /** Catch-all: fail closed with a generic body, but ALWAYS log the real exception. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        LOGGER.error("Unhandled exception - failing closed with 500", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    }

    private ResponseEntity<ErrorBody> build(HttpStatus status, String code, String message) {
        ErrorBody body = new ErrorBody(Instant.now().toString(), status.value(), status.getReasonPhrase(), code, message,
                MDC.get("traceId"));
        return ResponseEntity.status(status).body(body);
    }
}
