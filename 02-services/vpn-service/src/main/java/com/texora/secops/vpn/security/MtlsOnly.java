package com.texora.secops.vpn.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Dataplane-facing endpoint: authenticated by client certificate (mTLS), NOT by an SSO token. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MtlsOnly {
}
