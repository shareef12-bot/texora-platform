package com.texora.secops.ftp.exception;

public class UnprocessableException extends FtpException {

    public UnprocessableException(String message) {
        super(422, "UNPROCESSABLE", message);
    }

    public UnprocessableException(String code, String message) {
        super(422, code, message);
    }

    public UnprocessableException(String code, String message, Throwable cause) {
        super(422, code, message, cause);
    }
}
