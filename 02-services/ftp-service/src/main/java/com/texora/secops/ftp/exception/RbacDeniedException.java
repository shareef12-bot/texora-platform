package com.texora.secops.ftp.exception;

public class RbacDeniedException extends FtpException {

    public RbacDeniedException(String message) {
        super(403, "RBAC_DENIED", message);
    }

    public RbacDeniedException(String code, String message) {
        super(403, code, message);
    }

    public RbacDeniedException(String code, String message, Throwable cause) {
        super(403, code, message, cause);
    }
}
