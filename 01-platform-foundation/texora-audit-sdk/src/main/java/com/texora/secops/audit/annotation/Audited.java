package com.texora.secops.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit emission.
 *
 * <p>The {@link com.texora.secops.audit.aspect.AuditingAspect} intercepts every
 * method annotated with {@code @Audited} and, on successful return, emits an
 * immutable audit event containing:
 * <ul>
 *   <li>tenant_id — from {@link com.texora.secops.starter.domain.TenantContext}</li>
 *   <li>actor_id  — from the current security principal</li>
 *   <li>action    — the {@link #action()} value</li>
 *   <li>target_type — the {@link #targetType()} value</li>
 *   <li>target_id — resolved from the method return value or first argument</li>
 *   <li>outcome   — SUCCESS (on return) or FAILURE (on exception)</li>
 *   <li>occurred_at — wall-clock time at point of emission</li>
 *   <li>payload   — optional serialised context from {@link #includePayload()}</li>
 * </ul>
 *
 * <p>Emission is fail-safe: exceptions during audit writing are logged at ERROR
 * level but <strong>never propagated to the caller</strong>. The local durable
 * record is written synchronously; Kafka publishing is asynchronous.
 *
 * <p>Usage:
 * <pre>{@code
 * @Audited(action = "USER_CREATED", targetType = "USER")
 * public UserDto createUser(CreateUserRequest request) {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /**
     * The action verb emitted in the audit event (e.g. {@code "USER_CREATED"},
     * {@code "POLICY_APPROVED"}, {@code "CONFIG_CHANGED"}).
     * Use SCREAMING_SNAKE_CASE; keep it consistent with the service's event catalogue.
     */
    String action();

    /**
     * The type of resource being acted upon (e.g. {@code "USER"}, {@code "POLICY"},
     * {@code "VPN_GATEWAY"}). Used as {@code target_type} in the audit event.
     */
    String targetType();

    /**
     * When true, the method's first argument is serialised as JSON and included
     * in the audit event's {@code payload} field. Use with care — do not include
     * credentials or PII unless the audit store is appropriately secured.
     * Defaults to false.
     */
    boolean includePayload() default false;

    /**
     * When true, an audit event is emitted even if the method throws an exception,
     * with {@code outcome = FAILURE}. Defaults to true.
     */
    boolean auditFailures() default true;
}
