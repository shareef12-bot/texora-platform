package com.texora.secops.dc.exception;

import org.springframework.http.HttpStatus;

/**
 * DC business/authorization failure carrying the HTTP status and a stable machine code (B.7 error body).
 * This is a service-specific exception, handled by {@code DcGlobalExceptionHandler}; it is NOT a replacement
 * for the shared PlatformException hierarchy.
 */
public class DcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    public DcException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static DcException unauthenticated(String message) {
        return new DcException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }

    public static DcException forbidden(String code, String message) {
        return new DcException(HttpStatus.FORBIDDEN, code, message);
    }

    public static DcException notFound(String message) {
        return new DcException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static DcException conflict(String code, String message) {
        return new DcException(HttpStatus.CONFLICT, code, message);
    }

    public static DcException unprocessable(String code, String message) {
        return new DcException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static DcException badRequest(String code, String message) {
        return new DcException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static DcException upstreamTimeout(String message) {
        return new DcException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", message);
    }
}
