package com.texora.secops.ftp.exception;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Implements Shared Standards B.5: cross-tenant access is a 403, never an empty result.
 * Repositories are always tenant-filtered; when the tenant-filtered lookup is empty we check whether the id
 * exists at all. Exists elsewhere -> 403 (TENANT_ACCESS_DENIED). Does not exist -> 404.
 */
public final class TenantAccess {

    private TenantAccess() {
    }

    public static <T> T require(Optional<T> found, BooleanSupplier existsInAnyTenant, String what) {
        if (found.isPresent()) {
            return found.get();
        }
        if (existsInAnyTenant.getAsBoolean()) {
            throw new TenantAccessDeniedException(what + " belongs to another tenant");
        }
        throw new NotFoundException(what + " not found");
    }
}
