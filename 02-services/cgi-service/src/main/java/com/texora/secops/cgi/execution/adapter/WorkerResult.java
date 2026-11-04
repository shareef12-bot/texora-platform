package com.texora.secops.cgi.execution.adapter;

/** What a handler produced. The body is never logged or stored by the gateway. */
public final class WorkerResult {

    private final int exitCode;
    private final int httpStatus;
    private final String contentType;
    private final byte[] body;

    public WorkerResult(int exitCode, int httpStatus, String contentType, byte[] body) {
        this.exitCode = exitCode;
        this.httpStatus = httpStatus;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body.clone();
    }

    public int getExitCode() {
        return exitCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public int bodyLength() {
        return body.length;
    }

    @Override
    public String toString() {
        return "WorkerResult{exitCode=" + exitCode + ", httpStatus=" + httpStatus + ", bodyBytes=" + body.length + "}";
    }
}
