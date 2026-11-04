package com.texora.secops.ftp.security;

import com.texora.secops.starter.domain.TenantContext;

import java.util.UUID;

/**
 * The ONLY place in this service that touches the shared {@link TenantContext}, so that any change to that
 * class's API is a one-file fix. (The class itself is the shared one - it is not copied here.)
 */
public final class TenantBinder {

    private TenantBinder() {
    }

    public static void bind(UUID tenantId) {
        TenantContext.set(tenantId);
    }

    public static void clear() {
        TenantContext.clear();
    }
}
