package com.texora.secops.audit.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.annotation.Audited;
import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.kafka.AuditKafkaPublisher;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.starter.domain.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * AOP aspect that intercepts {@link Audited}-annotated service methods and
 * emits an immutable {@link AuditEvent}.
 *
 * <p><strong>Emission sequence:</strong>
 * <ol>
 *   <li>The annotated method executes.</li>
 *   <li>On return (or exception, if {@link Audited#auditFailures()} is true):
 *     <ol>
 *       <li>Build the {@link AuditEvent} (synchronous, in-transaction).</li>
 *       <li>Persist to the local DB — the event is durable before Kafka publish.</li>
 *       <li>Hand off to {@link AuditKafkaPublisher#publishAsync} — off the request thread.</li>
 *     </ol>
 *   </li>
 *   <li>Audit failures are <strong>never propagated</strong> to the caller —
 *       they are logged at ERROR level and the business response proceeds normally.</li>
 * </ol>
 *
 * <p>The aspect uses {@code REQUIRES_NEW} propagation for the audit persist so
 * that a rollback of the business transaction does not lose the audit record.
 * (We still want to know that an operation was attempted even if it failed.)
 */
@Aspect
public class AuditingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditingAspect.class);

    private final AuditEventRepository repository;
    private final AuditKafkaPublisher  kafkaPublisher;
    private final ObjectMapper         objectMapper;
    private final String               serviceName;

    public AuditingAspect(AuditEventRepository repository,
                          AuditKafkaPublisher kafkaPublisher,
                          ObjectMapper objectMapper,
                          String serviceName) {
        this.repository     = repository;
        this.kafkaPublisher = kafkaPublisher;
        this.objectMapper   = objectMapper;
        this.serviceName    = serviceName;
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {

        Throwable caughtException = null;
        Object result = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            caughtException = ex;
        }

        // Determine outcome
        boolean isFailure = (caughtException != null);
        if (isFailure && !audited.auditFailures()) {
            // Caller said don't audit failures — rethrow without auditing
            throw caughtException;
        }

        // Emit audit — never let this block or propagate exceptions to the caller
        try {
            emitAuditEvent(audited, joinPoint, result, isFailure);
        } catch (Exception auditEx) {
            LOGGER.error("AUDIT EMISSION FAILED for action='{}' service='{}' — "
                + "business operation still succeeds: {}",
                audited.action(), serviceName, auditEx.getMessage(), auditEx);
        }

        // Re-throw the original business exception if there was one
        if (caughtException != null) {
            throw caughtException;
        }
        return result;
    }

    // -------------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitAuditEvent(Audited audited, ProceedingJoinPoint joinPoint,
                               Object returnValue, boolean isFailure) {

        UUID tenantId = resolveTenantId();
        UUID actorId  = resolveActorId();
        String targetId = resolveTargetId(returnValue, joinPoint);
        String payload  = resolvePayload(audited, joinPoint);

        AuditEvent event = new AuditEvent(
            UUID.randomUUID(),
            tenantId,
            actorId,
            audited.action(),
            audited.targetType(),
            targetId,
            isFailure ? AuditEvent.OUTCOME_FAILURE : AuditEvent.OUTCOME_SUCCESS,
            Instant.now(),
            payload,
            serviceName
        );

        // Step 1: durable local record (synchronous, in REQUIRES_NEW transaction)
        repository.save(event);
        LOGGER.debug("Audit event {} persisted locally: action={} outcome={}",
            event.getId(), event.getAction(), event.getOutcome());

        // Step 2: async Kafka publish — does NOT block this transaction
        kafkaPublisher.publishAsync(event);
    }

    // -------------------------------------------------------------------------
    // Resolvers
    // -------------------------------------------------------------------------

    private UUID resolveTenantId() {
        UUID tenantId = TenantContext.getOrNull();
        if (tenantId == null) {
            LOGGER.warn("TenantContext is not set during audit emission — using UNKNOWN tenant");
            return new UUID(0, 0); // sentinel: 00000000-0000-0000-0000-000000000000
        }
        return tenantId;
    }

    private UUID resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            LOGGER.warn("No authenticated principal during audit emission — using UNKNOWN actor");
            return new UUID(0, 0);
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            // Principal name is not a UUID (e.g. email) — hash to a deterministic UUID
            return UUID.nameUUIDFromBytes(auth.getName().getBytes());
        }
    }

    /**
     * Tries to resolve a target ID from the method return value (if it has a
     * {@code getId()} method), or falls back to the string representation of the
     * first argument.
     */
    private String resolveTargetId(Object returnValue, ProceedingJoinPoint joinPoint) {
        if (returnValue != null) {
            try {
                Object id = returnValue.getClass().getMethod("getId").invoke(returnValue);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception ignored) {
                // Return value has no getId() — fall through
            }
        }

        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] != null) {
            // Try getId() on the first arg (e.g. a DTO)
            try {
                Object id = args[0].getClass().getMethod("getId").invoke(args[0]);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception ignored) {
                // No getId() on first arg — use toString() truncated
            }
            String str = args[0].toString();
            return str.length() > 255 ? str.substring(0, 255) : str;
        }

        return null;
    }

    private String resolvePayload(Audited audited, ProceedingJoinPoint joinPoint) {
        if (!audited.includePayload()) {
            return null;
        }
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(args[0]);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Could not serialise audit payload for action='{}': {}",
                audited.action(), ex.getMessage());
            return "{\"error\":\"payload serialisation failed\"}";
        }
    }
}
