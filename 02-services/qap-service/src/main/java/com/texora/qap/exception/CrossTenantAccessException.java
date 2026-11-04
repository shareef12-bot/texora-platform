package com.texora.qap.exception;

public class CrossTenantAccessException extends RuntimeException {
    public CrossTenantAccessException(String message) {
        super(message);
    }
}
