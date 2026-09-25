package com.texora.secops.sso.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.AuditSdk;
import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.domain.AuditOutcome;
import com.texora.secops.sso.domain.SsoAuditEvent;
import com.texora.secops.sso.repository.SsoAuditEventRepository;
import com.texora.secops.sso.tenant.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps every {@link Audited} service method so that a matching
 * sso_audit_event row is written AND published to Kafka topic
 * sso.audit.events (via the shared Audit SDK) regardless of whether the
 * method succeeds, throws, or the underlying business logic itself denies
 * the action. This is what makes "audit event ALWAYS" (LLD §5.3 step 6, §9.3)
 * structural rather than a convention every developer has to remember.
 *
 * <p>Explicit bean name "ssoAuditingAspect" — the shared texora-audit-sdk's
 * AuditAutoConfiguration also registers a bean literally named
 * "auditingAspect" (its @Bean factory method name), which is Spring's
 * default component-scan name for THIS class too (decapitalized simple
 * class name), causing a BeanDefinitionOverrideException on startup if
 * left unqualified. Both beans are legitimately needed — this one wraps
 * @Audited SSO methods, the SDK's is a different generic aspect — so the
 * fix is disambiguating the name, not removing either bean.
 */
@Aspect
@Component("ssoAuditingAspect")
public class AuditingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditingAspect.class);

    private final SsoAuditEventRepository auditEventRepository;
    private final AuditSdk auditSdk;
    private final ObjectMapper objectMapper;

    public AuditingAspect(SsoAuditEventRepository auditEventRepository, AuditSdk auditSdk,
                           ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.auditSdk = auditSdk;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object emitAudit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditOutcome outcome;
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            outcome = AuditOutcome.SUCCESS;
        } catch (com.texora.secops.sso.exception.FailClosedException | com.texora.secops.sso.exception.RbacDeniedException e) {
            outcome = AuditOutcome.DENIED;
            failure = e;
        } catch (Throwable t) {
            outcome = AuditOutcome.FAILURE;
            failure = t;
        }

        // Captured AFTER proceed(), not before: for most endpoints tenant is already
        // set by TenantResolvingFilter before the controller (and this aspect) ever
        // run, so this makes no difference. But login.attempt is the one case where
        // TenantContext isn't populated until PARTWAY THROUGH the audited method
        // itself (LoginOrchestrationService resolves tenant from the target
        // application, since there's no bearer token yet to have derived it from) —
        // capturing before proceed() would always record tenantId=null for logins.
        UUID tenantId;
        try {
            tenantId = TenantContext.get();
        } catch (IllegalStateException e) {
            tenantId = null; // e.g. application/tenant itself couldn't be resolved
        }

        try {
            record(tenantId, audited, joinPoint.getArgs(), outcome);
        } catch (Exception auditWriteError) {
            // The audit write itself must never mask the original business
            // outcome, but a failure to audit is loud in the logs, not silent.
            LOGGER.error("Failed to persist/emit audit event for action={}: {}",
                    audited.action(), auditWriteError.getMessage());
        }

        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private void record(UUID tenantId, Audited audited, Object[] args, AuditOutcome outcome) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("args", argsSummary(args)));
        } catch (Exception e) {
            payload = "{}";
        }

        SsoAuditEvent event = new SsoAuditEvent(UUID.randomUUID(), tenantId, null, audited.action(),
                audited.targetType(), null, outcome, Instant.now(), payload);
        auditEventRepository.save(event);
        auditSdk.publish("sso.audit.events", event.getId().toString(), toAuditPayload(event));
    }

    private String argsSummary(Object[] args) {
        return args == null ? "[]" : java.util.Arrays.toString(args);
    }

    private String toAuditPayload(SsoAuditEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", event.getId().toString(),
                    "tenantId", event.getTenantId() != null ? event.getTenantId().toString() : "",
                    "action", event.getAction(),
                    "targetType", event.getTargetType(),
                    "outcome", event.getOutcome().name(),
                    "occurredAt", event.getOccurredAt().toString()));
        } catch (Exception e) {
            return "{}";
        }
    }
}