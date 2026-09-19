package com.texora.secops.starter.domain;

/**
 * Thrown when a repository or service requires a tenant ID but none is present
 * in {@link TenantContext}. This is always a configuration or security bug —
 * every authenticated request must set a tenant before reaching the data layer.
 */
public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }

    public TenantContextMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
