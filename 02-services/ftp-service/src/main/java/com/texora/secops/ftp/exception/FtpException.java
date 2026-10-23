package com.texora.secops.ftp.exception;

/**
 * Base class for FTP-service business/HTTP exceptions. This is NOT a replacement for the shared
 * PlatformException hierarchy: it only carries service-specific codes (e.g. ENDPOINT_NOT_ALLOWLISTED,
 * SELF_RELEASE_BLOCKED) and is mapped by this service's own GlobalExceptionHandler.
 */
public class FtpException extends RuntimeException {

    private final int status;
    private final String code;

    public FtpException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public FtpException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
