package com.texora.secops.starter.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Objects;

/**
 * Standard error response body produced by all Texora SecOps services (standard B.7).
 *
 * <pre>{@code
 * {
 *   "timestamp": "2026-09-15T10:00:00Z",
 *   "status": 403,
 *   "error": "Forbidden",
 *   "code": "RBAC_DENIED",
 *   "message": "Caller lacks required role",
 *   "traceId": "abc123"
 * }
 * }</pre>
 *
 * Immutable after construction. No Lombok — explicit getters and constructors only.
 */
public final class ApiError {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String traceId;

    public ApiError(Instant timestamp, int status, String error,
                    String code, String message, String traceId) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.status    = status;
        this.error     = Objects.requireNonNull(error,   "error");
        this.code      = Objects.requireNonNull(code,    "code");
        this.message   = Objects.requireNonNull(message, "message");
        this.traceId   = traceId; // nullable — may be absent in tests
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApiError)) {
            return false;
        }
        ApiError that = (ApiError) other;
        return status == that.status
            && Objects.equals(timestamp, that.timestamp)
            && Objects.equals(error, that.error)
            && Objects.equals(code, that.code)
            && Objects.equals(message, that.message)
            && Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, status, error, code, message, traceId);
    }

    @Override
    public String toString() {
        return "ApiError{status=" + status + ", code='" + code + "', message='" + message + "'}";
    }
}
