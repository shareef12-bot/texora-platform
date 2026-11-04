package com.texora.secops.siem.web;

public class UpstreamTimeoutException extends RuntimeException {

    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
