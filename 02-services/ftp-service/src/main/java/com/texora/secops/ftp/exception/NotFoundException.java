package com.texora.secops.ftp.exception;

public class NotFoundException extends FtpException {

    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(404, code, message);
    }

    public NotFoundException(String code, String message, Throwable cause) {
        super(404, code, message, cause);
    }
}
