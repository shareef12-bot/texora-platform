package com.texora.secops.ftp.exception;

public class UpstreamUnavailableException extends FtpException {

    public UpstreamUnavailableException(String message) {
        super(504, "UPSTREAM_UNAVAILABLE", message);
    }

    public UpstreamUnavailableException(String code, String message) {
        super(504, code, message);
    }

    public UpstreamUnavailableException(String code, String message, Throwable cause) {
        super(504, code, message, cause);
    }
}
