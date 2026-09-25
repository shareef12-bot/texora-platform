package com.texora.secops.sec.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local tenant context resolved from the SSO token or mTLS client cert.
 * Tenant is NEVER resolved from a request parameter (B.5).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Returns the current tenant ID or throws 403 Forbidden (not 401 — the
     * caller is authenticated but we cannot determine their tenant, which is
     * an authorization failure, not an authentication failure).
     */
    public static UUID requireTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tenant context not established — missing tenant claim in token");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
