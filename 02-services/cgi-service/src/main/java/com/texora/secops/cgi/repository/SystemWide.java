package com.texora.secops.cgi.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the ONLY repository methods that are deliberately not tenant-filtered. Allowed uses:
 * (1) the system audit-forwarding job, which works across tenants and carries tenant_id forward;
 * (2) a bare existence check used solely to tell a cross-tenant 403 from a genuine 404.
 * {@code RepositoryTenantScopeTest} fails the build if any other method lacks tenant scoping.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SystemWide {
}
