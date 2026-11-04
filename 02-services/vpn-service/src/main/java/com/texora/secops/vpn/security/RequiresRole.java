package com.texora.secops.vpn.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Caller must hold at least one of the listed roles. An /api/v1 handler with neither this nor @MtlsOnly is DENIED. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

    String[] value();
}
