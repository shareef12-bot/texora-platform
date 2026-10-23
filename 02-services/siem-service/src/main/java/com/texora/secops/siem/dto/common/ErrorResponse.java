package com.texora.secops.siem.dto.common;

import java.time.Instant;
import java.util.Objects;

/** Standard error body shape, exactly per shared standards B.7. */
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String traceId;

    public ErrorResponse() {
        // required for JSON deserialization
    }

    public ErrorResponse(Instant timestamp, int status, String error, String code, String message, String traceId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ErrorResponse)) {
            return false;
        }
        ErrorResponse that = (ErrorResponse) other;
        return status == that.status && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, code);
    }
}
