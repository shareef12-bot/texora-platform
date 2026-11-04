package com.texora.secops.ftp.exception;

import com.texora.secops.ftp.dto.ErrorResponse;
import com.texora.secops.starter.exception.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * FTP-specific exception mapping. The explicit, unique bean name avoids the BeanDefinitionOverrideException
 * with the starter's "globalExceptionHandler" (KNOWN-INTEGRATION-PITFALLS #3).
 *
 * Deliberately NO catch-all {@code @ExceptionHandler(Exception.class)}: an advice that catches Exception can shadow
 * the starter's handlers for PlatformException subclasses and turn 403s into 500s. Unmapped exceptions fall through
 * to the starter handler / Spring's default 500, both of which log. Every handler here logs before responding.
 */
@RestControllerAdvice
@Component("ftpGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FtpException.class)
    public ResponseEntity<ErrorResponse> handleFtp(FtpException e) {
        if (e.getStatus() >= 500) {
            LOGGER.error("FTP service error {} ({})", e.getCode(), e.getStatus(), e);
        } else {
            LOGGER.info("Request rejected: {} {} - {}", e.getStatus(), e.getCode(), e.getMessage());
        }
        return build(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(PlatformException.UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(PlatformException.UnauthenticatedException e) {
        LOGGER.info("Unauthenticated request rejected: {}", e.getClass().getSimpleName());
        return build(401, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map((FieldError f) -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        LOGGER.info("Validation failed: {}", message);
        return build(422, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception e) {
        LOGGER.info("Malformed request: {}", e.getClass().getSimpleName());
        return build(400, "MALFORMED_REQUEST", "Malformed request");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        LOGGER.warn("Data integrity violation", e);
        return build(409, "CONFLICT", "Request conflicts with existing data");
    }

    private ResponseEntity<ErrorResponse> build(int status, String code, String message) {
        String reason = HttpStatus.valueOf(status).getReasonPhrase();
        ErrorResponse body = new ErrorResponse(Instant.now(), status, reason, code, message, MDC.get("traceId"));
        return ResponseEntity.status(status).body(body);
    }
}
