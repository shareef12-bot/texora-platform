package com.texora.secops.siem.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the RBAC role(s) required to invoke a controller method, e.g.
 * {@code @RequiresRole("siem.rule.write")}. The caller must hold at least one of the listed
 * roles. Enforced by {@link RbacInterceptor} before the handler runs (B.6.2) - never enforce
 * this in the controller body.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    String[] value();
}
