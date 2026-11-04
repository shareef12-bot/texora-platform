package com.texora.secops.cgi.execution.adapter;

/** The adapter could not (or refused to) start a worker. The message is a short code; it must never hold request data. */
public class WorkerSpawnException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WorkerSpawnException(String reasonCode) {
        super(reasonCode);
    }

    public WorkerSpawnException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
    }
}
