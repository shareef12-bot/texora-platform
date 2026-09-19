package com.texora.secops.sso.tenant;

import java.util.UUID;

/**
 * Holds the tenant_id resolved from the caller's SSO token claim for the
 * duration of the current request thread. Tenant is NEVER accepted from a
 * request parameter (per shared standards B.5) — only from the validated
 * token, via {@link TenantResolvingFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID get() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant resolved on the current request thread");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
