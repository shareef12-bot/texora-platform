package com.texora.secops.siem.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.starter.exception.TenantContextMissingException;

import java.util.UUID;

/**
 * Thread-local holder for the current request's validated {@link TokenClaims}, per the confirmed
 * {@code texora-iam-client} API (KNOWN-INTEGRATION-PITFALLS.md item 1). Populated by
 * {@link RbacInterceptor#preHandle} and always cleared in {@code afterCompletion} so nothing
 * leaks across pooled request-handling threads.
 *
 * <p><b>Deliberately NOT named {@code TenantContext}.</b> {@code texora-secops-starter} almost
 * certainly already ships its own shared tenant-context utility - its existence is implied by
 * {@code TenantContextMissingException} living at {@code com.texora.secops.starter.exception}
 * (pitfalls item 5), which this class throws below precisely because that failure case belongs
 * to the shared exception vocabulary. But the shared utility's own class name, package, and
 * method signatures were not available to confirm at build time, and per the pitfalls doc's own
 * central lesson (item 1: "do not invent a different shape"), guessing them would repeat exactly
 * the mistake that document exists to prevent. This class is a small, local, service-scoped
 * stand-in - reusing the name {@code TenantContext} for it would risk masking or colliding with
 * the real shared class once it's confirmed. <b>Replace this class's usages with the real shared
 * utility once its API is confirmed from the actual texora-secops-starter source.</b>
 *
 * <p>{@link #PLATFORM_TENANT_ID} is the reserved tenant id used for rows that are not owned by a
 * single business tenant - the ingestion-channel tables (see V1 migration). It is never a valid
 * caller tenant and must never satisfy a tenant-scope comparison for search or alert data.
 */
public final class CallerContext {

    public static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final ThreadLocal<TokenClaims> CURRENT = new ThreadLocal<>();

    private CallerContext() {
    }

    public static void set(TokenClaims claims) {
        CURRENT.set(claims);
    }

    public static TokenClaims get() {
        return CURRENT.get();
    }

    public static UUID currentTenantId() {
        TokenClaims claims = CURRENT.get();
        if (claims == null) {
            // Confirmed shared exception type (pitfalls item 5) rather than a locally-invented
            // IllegalStateException - this is exactly the "no tenant context available, fail
            // closed" case it exists for.
            throw new TenantContextMissingException(
                    "No validated caller on this thread - fail closed upstream of this call");
        }
        return claims.getTenantId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
