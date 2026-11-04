package com.texora.secops.ftp.exception;

public class ConflictException extends FtpException {

    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(409, code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(409, code, message, cause);
    }
}
