package com.texora.secops.siem.audit;

import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.CallerContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Wraps every {@link Audited}-annotated service method: on success, audits with {@code SUCCESS}
 * and the method's arguments as payload; on any thrown exception, audits with {@code FAILURE}
 * (or {@code DENIED} for an access-control exception) and re-throws unchanged. The audit write
 * itself is never allowed to suppress or alter the underlying method's outcome - a failure to
 * write the audit record is logged, not thrown, so auditing can never accidentally turn a
 * legitimate action into an error (or vice versa).
 *
 * <p>Target id resolution is a best-effort convention, not reflection magic that must be
 * perfectly generic: if the return value exposes a public {@code getId()}, that value is used;
 * otherwise the first {@code UUID}-typed method argument is used; otherwise the target id is
 * left null and only {@code targetType} is recorded.
 */
@Aspect
@Component("siemAuditingAspect")
public class AuditingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditingAspect.class);

    private final AuditEventPublisher auditEventPublisher;

    public AuditingAspect(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object[] args = joinPoint.getArgs();
        UUID tenantId = resolveTenantId();
        UUID actorId = resolveActorId();

        try {
            Object result = joinPoint.proceed();
            safePublish(tenantId, actorId, audited, resolveTargetId(result, args), AuditOutcome.SUCCESS, args);
            return result;
        } catch (Throwable throwable) {
            AuditOutcome outcome = isAccessDenied(throwable) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
            safePublish(tenantId, actorId, audited, resolveTargetId(null, args), outcome, args);
            throw throwable;
        }
    }

    private void safePublish(UUID tenantId, UUID actorId, Audited audited, String targetId,
                              AuditOutcome outcome, Object[] args) {
        try {
            auditEventPublisher.publish(tenantId, actorId, audited.action(), audited.targetType(),
                    targetId, outcome, args);
        } catch (Exception publishFailure) {
            LOGGER.error("Failed to record audit event for action {} - this is logged, not thrown, "
                    + "so it cannot mask the real outcome of the underlying operation", audited.action(), publishFailure);
        }
    }

    private boolean isAccessDenied(Throwable throwable) {
        String name = throwable.getClass().getSimpleName();
        return name.contains("AccessDenied") || name.contains("CrossTenant") || name.contains("SelfApproval");
    }

    private UUID resolveTenantId() {
        TokenClaims principal = CallerContext.get();
        return principal != null ? principal.getTenantId() : CallerContext.PLATFORM_TENANT_ID;
    }

    private UUID resolveActorId() {
        TokenClaims principal = CallerContext.get();
        return principal != null ? principal.getSubject() : SystemActor.ID;
    }

    private String resolveTargetId(Object result, Object[] args) {
        UUID fromResult = tryGetId(result);
        if (fromResult != null) {
            return fromResult.toString();
        }
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof UUID uuid) {
                    return uuid.toString();
                }
            }
        }
        return null;
    }

    private UUID tryGetId(Object target) {
        if (target == null) {
            return null;
        }
        try {
            Method getId = target.getClass().getMethod("getId");
            Object value = getId.invoke(target);
            return value instanceof UUID uuid ? uuid : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
