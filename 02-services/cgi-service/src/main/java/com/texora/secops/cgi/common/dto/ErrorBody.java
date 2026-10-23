package com.texora.secops.cgi.common.dto;

import java.util.Objects;

/**
 * Standard error body (Shared Standards B.7).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class ErrorBody {

    private String timestamp;

    private int status;

    private String error;

    private String code;

    private String message;

    private String traceId;

    public ErrorBody() {
        // default constructor (JSON / property binding)
    }

    public ErrorBody(String timestamp, int status, String error, String code, String message, String traceId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.traceId = traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
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
        if (!(other instanceof ErrorBody)) {
            return false;
        }
        ErrorBody that = (ErrorBody) other;
        return Objects.equals(timestamp, that.timestamp) && Objects.equals(status, that.status) && Objects.equals(error, that.error) && Objects.equals(code, that.code) && Objects.equals(message, that.message) && Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, status, error, code, message, traceId);
    }

    @Override
    public String toString() {
        return "ErrorBody{" + "timestamp=" + timestamp + ", status=" + status + ", error=" + error + ", code=" + code + ", message=" + message + ", traceId=" + traceId + "}";
    }

}
