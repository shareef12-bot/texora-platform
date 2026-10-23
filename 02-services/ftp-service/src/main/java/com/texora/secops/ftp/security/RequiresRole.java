package com.texora.secops.ftp.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-endpoint RBAC declaration, enforced by {@link RbacInterceptor} BEFORE the handler runs.
 * The caller must hold at least one of the listed roles. A /api/v1 handler WITHOUT this annotation is denied
 * (fail closed).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {

    String[] value();
}
