package com.texora.secops.cgi.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any valid SSO token is enough at the interceptor; a further decision is made in the handler itself
 * (used by the gateway entrypoint, where the SEC route-level policy decision follows).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuthenticatedOnly {
}
