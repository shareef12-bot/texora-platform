package com.texora.secops.starter.exception;

/**
 * Thrown by {@link com.texora.secops.starter.domain.TenantContext#get()} when
 * a repository/service reaches into TenantContext but no tenant ID was ever
 * set — meaning the request reached business logic without going through
 * authentication. Mapped to HTTP 401 by {@link GlobalExceptionHandler},
 * since this always indicates a missing/misconfigured auth filter, never a
 * legitimate business condition.
 */
public class TenantContextMissingException extends RuntimeException {
    public TenantContextMissingException(String message) {
        super(message);
    }
}