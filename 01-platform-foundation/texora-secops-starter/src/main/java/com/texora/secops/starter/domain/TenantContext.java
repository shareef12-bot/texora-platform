package com.texora.secops.starter.domain;

import com.texora.secops.starter.exception.TenantContextMissingException;

import java.util.UUID;

/**
 * Thread-local store for the tenant ID resolved from the SSO token.
 *
 * <p>Populated by the IAM client's {@code RbacInterceptor} (or equivalent
 * servlet filter) after token validation. Every repository method retrieves
 * the tenant ID from here — never from a request parameter (standard B.5).
 *
 * <p><strong>Usage contract:</strong>
 * <ol>
 *   <li>Set at the beginning of every authenticated request.</li>
 *   <li>Clear in a finally block or filter afterCompletion to prevent leaks
 *       across thread-pool reuse.</li>
 * </ol>
 *
 * <pre>{@code
 *   // In your filter / interceptor:
 *   TenantContext.set(resolvedTenantId);
 *   try {
 *       chain.doFilter(request, response);
 *   } finally {
 *       TenantContext.clear();
 *   }
 * }</pre>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    /**
     * Sets the tenant ID for the current request thread.
     *
     * @param tenantId must not be null
     * @throws IllegalArgumentException if tenantId is null
     */
    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Returns the tenant ID for the current request thread.
     *
     * @return the current tenant ID
     * @throws TenantContextMissingException if no tenant ID has been set,
     *         which indicates a missing authentication filter or misconfiguration
     */
    public static UUID get() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantContextMissingException(
                "No tenant ID in TenantContext — request reached repository without authentication. " +
                "Ensure the IAM filter is registered and token validation succeeded.");
        }
        return tenantId;
    }

    /**
     * Returns the tenant ID, or null if not set. Use only in tests or
     * in infrastructure code that must handle the unauthenticated case.
     */
    public static UUID getOrNull() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clears the tenant ID from the current thread. Must be called in a
     * finally block after request processing completes.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Returns true if a tenant ID is set for the current thread.
     */
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }
}
