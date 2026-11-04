package com.texora.secops.vpn.dto;

public class ErrorBody {

    private String timestamp;

    private int status;

    private String error;

    private String code;

    private String message;

    private String traceId;

    public ErrorBody() {
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
        return "ErrorBody{" + "timestamp=" + timestamp + ", status=" + status + ", error=" + error + ", code=" + code + ", message=" + message + ", traceId=" + traceId + "}";
    }

}
