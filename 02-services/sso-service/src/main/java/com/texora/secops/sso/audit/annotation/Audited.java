package com.texora.secops.sso.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose invocation must always emit an immutable
 * audit event — success, failure, or denial — via AuditingAspect. Applied to
 * every mutating action and every policy decision per shared standards B.6.4.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    String action();

    String targetType();
}
