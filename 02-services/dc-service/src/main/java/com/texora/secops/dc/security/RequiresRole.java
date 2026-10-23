package com.texora.secops.dc.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the single role required to invoke a controller method. Enforced by {@link RbacInterceptor}
 * BEFORE the handler runs. A handler under /api/v1 with no {@code @RequiresRole} is DENIED (fail closed).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

    String value();
}
