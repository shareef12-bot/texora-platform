package com.texora.secops.ftp.exception;

public class FtpUnauthenticatedException extends FtpException {

    public FtpUnauthenticatedException(String message) {
        super(401, "UNAUTHENTICATED", message);
    }

    public FtpUnauthenticatedException(String code, String message) {
        super(401, code, message);
    }

    public FtpUnauthenticatedException(String code, String message, Throwable cause) {
        super(401, code, message, cause);
    }
}
