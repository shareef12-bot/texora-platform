package com.texora.secops.siem.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose invocation must produce an immutable audit event (B.6.4): every
 * mutating action and every policy decision. Picked up by {@link AuditingAspect}, which emits
 * the event via {@link AuditEventPublisher} regardless of whether the method succeeds or throws
 * - a rejected or failed action is exactly as audit-worthy as a successful one.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Short action code, e.g. "correlation-rule.submit", "alert.transition". */
    String action();

    /** The domain type being acted on, e.g. "SiemCorrelationRule", "SiemAlert". */
    String targetType();
}
