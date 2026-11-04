package com.texora.secops.sec.audit.aspect;

import com.texora.secops.sec.audit.service.AuditChainService;
import com.texora.secops.starter.domain.TenantContext;
import com.texora.secops.starter.exception.TenantContextMissingException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Feeds SEC's OWN tamper-evident, hash-chained audit log ({@link AuditChainService}).
 *
 * <p><strong>Why this is not the shared {@code AuditingAspect}:</strong> the shared aspect
 * (texora-audit-sdk) publishes each service's normal audit events to Kafka. SEC additionally keeps a
 * hash-chained, scheduled-verified ledger that no other service has, and
 * that is what this class writes. It is deliberately NOT named {@code AuditingAspect}: that would give
 * the bean the name {@code auditingAspect} and collide with the shared aspect's bean.</p>
 *
 * <p>The two mechanisms are complementary, not duplicates: the same service methods carry the shared
 * {@code @Audited} (normal audit stream) and are also matched by the pointcuts below (chain).
 * Pointcuts are by method signature rather than by annotation, so the chain's coverage (including
 * FAILURES such as rejected self-approvals) does not depend on what the shared annotation records.</p>
 *
 * <p>Failure to write the chain entry is logged and does not disrupt the primary operation (unchanged
 * pre-refactor behaviour).</p>
 */
@Aspect
@Component
public class SecAuditChainAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecAuditChainAspect.class);

    private final AuditChainService auditChainService;

    public SecAuditChainAspect(AuditChainService auditChainService) {
        this.auditChainService = auditChainService;
    }

    /**
     * Audit successful policy lifecycle mutations.
     */
    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.policy.service.PolicyLifecycleService.createPolicy(..))",
        returning = "result")
    public void auditCreatePolicy(JoinPoint jp, Object result) {
        emitAudit("POLICY_CREATE", "sec_policy", extractId(result), "SUCCESS");
    }

    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.policy.service.PolicyLifecycleService.activateVersion(..))",
        returning = "result")
    public void auditActivateVersion(JoinPoint jp, Object result) {
        emitAudit("POLICY_VERSION_ACTIVATE", "sec_policy_version", extractId(result), "SUCCESS");
    }

    /**
     * Audit privileged approval operations.
     */
    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.approval.service.PrivilegedApprovalService.submitRequest(..))",
        returning = "result")
    public void auditSubmitRequest(JoinPoint jp, Object result) {
        emitAudit("PRIVILEGED_REQUEST_SUBMIT", "sec_privileged_request", extractId(result), "SUCCESS");
    }

    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.approval.service.PrivilegedApprovalService.recordDecision(..))",
        returning = "result")
    public void auditRecordDecision(JoinPoint jp, Object result) {
        emitAudit("PRIVILEGED_REQUEST_DECISION", "sec_approval_decision", extractId(result), "SUCCESS");
    }

    /**
     * Audit secret reference operations.
     */
    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.secrets.service.SecretReferenceService.registerReference(..))",
        returning = "result")
    public void auditRegisterReference(JoinPoint jp, Object result) {
        emitAudit("SECRET_REFERENCE_REGISTER", "sec_secret_reference", extractId(result), "SUCCESS");
    }

    @AfterReturning(
        pointcut = "execution(* com.texora.secops.sec.secrets.service.SecretReferenceService.resolveReference(..))",
        returning = "result")
    public void auditResolveReference(JoinPoint jp, Object result) {
        emitAudit("SECRET_REFERENCE_RESOLVE", "sec_secret_reference", extractId(result), "SUCCESS");
    }

    /**
     * Audit failed operations.
     */
    @AfterThrowing(
        pointcut = "execution(* com.texora.secops.sec.policy.service.*.*(..))" +
                   " || execution(* com.texora.secops.sec.secrets.service.*.*(..))" +
                   " || execution(* com.texora.secops.sec.approval.service.*.*(..))",
        throwing = "ex")
    public void auditFailure(JoinPoint jp, Exception ex) {
        String methodName = jp.getSignature().getName();
        emitAudit(methodName.toUpperCase() + "_FAILURE", "unknown", null, "FAILURE");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void emitAudit(String action, String targetType, String targetId, String outcome) {
        try {
            UUID tenantId = resolveTenantOrNil();
            String actorId = resolveActorId();

            auditChainService.appendEvent(
                    tenantId,
                    actorId,
                    action,
                    targetType,
                    targetId,
                    outcome,
                    Map.of("source", "SecAuditChainAspect")
            );
        } catch (Exception ex) {
            // Audit failure is logged but must not disrupt the primary operation
            LOGGER.error("SecAuditChainAspect failed to emit audit event for action={}", action, ex);
        }
    }

    /** Nil UUID when no tenant is established (e.g. a failure before tenant resolution). */
    private UUID resolveTenantOrNil() {
        try {
            return TenantContext.get();
        } catch (TenantContextMissingException ex) {
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }

    private String resolveActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ex) {
            LOGGER.debug("Could not resolve actor from SecurityContext", ex);
        }
        return "system";
    }

    private String extractId(Object result) {
        if (result == null) return null;
        try {
            // Reflectively call getId() — all domain objects have it (no Lombok)
            return result.getClass().getMethod("getId").invoke(result).toString();
        } catch (Exception ex) {
            return result.toString();
        }
    }
}
