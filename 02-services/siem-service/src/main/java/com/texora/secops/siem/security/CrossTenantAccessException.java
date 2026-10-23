package com.texora.secops.siem.security;

/**
 * Thrown whenever a resource is found by id but belongs to a different tenant than the caller.
 * Always mapped to 403 Forbidden, never 404 - per B.5, "cross-tenant access returns 403
 * Forbidden, not an empty result", so a caller cannot distinguish "does not exist" from
 * "exists but not yours" by probing IDs, but also is never told the exact shape of data it
 * cannot see (see REQUIREMENT 3's adversarial reconnaissance concern).
 */
public class CrossTenantAccessException extends RuntimeException {

    public CrossTenantAccessException(String message) {
        super(message);
    }
}
