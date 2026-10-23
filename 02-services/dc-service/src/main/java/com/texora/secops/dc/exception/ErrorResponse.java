package com.texora.secops.dc.exception;

/** Standard error body (01-SHARED-STANDARDS B.7). */
public class ErrorResponse {

    private String timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String traceId;

    public ErrorResponse() {
        // required by Jackson
    }

    public ErrorResponse(String timestamp, int status, String error, String code, String message, String traceId) {
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
    public String toString() {
        return "ErrorResponse{status=" + status + ", code='" + code + "'}";
    }
}
