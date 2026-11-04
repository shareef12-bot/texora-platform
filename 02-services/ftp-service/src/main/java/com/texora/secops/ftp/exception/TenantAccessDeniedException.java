package com.texora.secops.ftp.exception;

public class TenantAccessDeniedException extends FtpException {

    public TenantAccessDeniedException(String message) {
        super(403, "TENANT_ACCESS_DENIED", message);
    }

    public TenantAccessDeniedException(String code, String message) {
        super(403, code, message);
    }

    public TenantAccessDeniedException(String code, String message, Throwable cause) {
        super(403, code, message, cause);
    }
}
