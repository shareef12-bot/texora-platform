package com.texora.secops.dc.audit.service;

import com.texora.secops.dc.domain.DcAuditEvent;
import com.texora.secops.dc.domain.DcAuditPublication;
import com.texora.secops.dc.infra.AsyncConfig;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.AuditEventRepository;
import com.texora.secops.dc.repository.AuditPublicationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * DC's durable audit writer (dc_audit_event + async Kafka publish to dc.audit.events).
 *
 * <p>WHY THIS EXISTS ALONGSIDE THE SHARED AUDIT SDK: the LLD requires a queryable, append-only dc_audit_event table
 * (GET /api/v1/audit), and policy-DECISION events carry an outcome that an annotation-based aspect cannot know.
 * This is NOT a copy of the shared AuditingAspect (it has no aspect/pointcut logic). It follows the same guarantee
 * mandated by KNOWN-INTEGRATION-PITFALLS #11: local durable write first, async Kafka publish, scheduled retry.
 * texora-audit-sdk stays on the classpath; see README "Unverified shared-library assumptions".
 *
 * <p>{@link #record} joins the caller's transaction (event commits iff the business change commits - no unaudited
 * mutation). {@link #recordIndependent} always commits (used for DENIED/failed attempts that roll the caller back).
 */
@Service
public class DcAuditRecorder {

    private static final long INITIAL_GRACE_SECONDS = 60L;

    private final AuditEventRepository events;
    private final AuditPublicationRepository publications;
    private final DcAuditPublisher publisher;
    private final TimeSource time;
    private final Executor publishExecutor;

    public DcAuditRecorder(AuditEventRepository events, AuditPublicationRepository publications,
                           DcAuditPublisher publisher, TimeSource time,
                           @Qualifier(AsyncConfig.DC_PUBLISH_EXECUTOR) Executor publishExecutor) {
        this.events = events;
        this.publications = publications;
        this.publisher = publisher;
        this.time = time;
        this.publishExecutor = publishExecutor;
    }

    @Transactional
    public DcAuditEvent record(UUID tenantId, UUID actorId, String action, String targetType, String targetId,
                               String outcome, Map<String, Object> payload) {
        return persist(tenantId, actorId, action, targetType, targetId, outcome, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DcAuditEvent recordIndependent(UUID tenantId, UUID actorId, String action, String targetType,
                                          String targetId, String outcome, Map<String, Object> payload) {
        return persist(tenantId, actorId, action, targetType, targetId, outcome, payload);
    }

    private DcAuditEvent persist(UUID tenantId, UUID actorId, String action, String targetType, String targetId,
                                 String outcome, Map<String, Object> payload) {
        Instant now = time.now();
        DcAuditEvent event = new DcAuditEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setActorId(actorId);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setOutcome(outcome);
        event.setOccurredAt(now);
        event.setPayload(payload);
        events.save(event);

        DcAuditPublication publication = new DcAuditPublication();
        publication.setAuditEventId(event.getId());
        publication.setTenantId(tenantId);
        publication.setAttempts(0);
        publication.setNextAttemptAt(now.plusSeconds(INITIAL_GRACE_SECONDS));
        publications.save(publication);

        final String json = publisher.toJson(event); // a serialization failure aborts the transaction (fail closed)
        final UUID eventId = event.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(eventId, tenantId, json);
                }
            });
        } else {
            submit(eventId, tenantId, json);
        }
        return event;
    }

    private void submit(UUID eventId, UUID tenantId, String json) {
        publishExecutor.execute(() -> publisher.publish(eventId, tenantId, json));
    }
}
