package com.texora.secops.cgi.audit.service;

import com.texora.secops.cgi.audit.domain.AuditEvent;
import com.texora.secops.cgi.audit.domain.AuditOutbox;
import com.texora.secops.cgi.repository.AuditEventRepository;
import com.texora.secops.cgi.repository.AuditOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * CGI's audit writer.
 *
 * <p><b>Why this is not the shared {@code @Audited}/AuditingAspect:</b> CGI_TDD requires a STRICTER guarantee than
 * the shared SDK gives other services - redaction before persistence, a SYNCHRONOUS local durable write, and an
 * ASYNCHRONOUS Kafka forward that can never block or fail request handling. This class provides exactly that
 * (local durable write first, async Kafka publish, scheduled retry for anything not yet published - see
 * {@link AuditForwarder}), as KNOWN-INTEGRATION-PITFALLS #11 permits when a service needs its own writer.
 * It is deliberately NOT named TenantContext/AuditingAspect and does not replace them.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #recordInTransaction} - admin mutations: joins the caller's transaction, so a mutation can never be
 *       committed without its audit row (and fails if the audit row cannot be written).</li>
 *   <li>{@link #recordNeverFail} - gateway/denial paths: own transaction, swallows every error.</li>
 * </ul>
 */
@Service
public class CgiAuditWriter {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String OUTCOME_FAILURE = "FAILURE";

    private static final Logger LOGGER = LoggerFactory.getLogger(CgiAuditWriter.class);

    private final AuditEventRepository auditEventRepository;
    private final AuditOutboxRepository auditOutboxRepository;
    private final ParameterRedactionService redactionService;
    private final AuditForwarder forwarder;
    private final TransactionTemplate requiresNewTemplate;
    private final Clock clock;

    public CgiAuditWriter(AuditEventRepository auditEventRepository, AuditOutboxRepository auditOutboxRepository,
                          ParameterRedactionService redactionService, AuditForwarder forwarder,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.auditOutboxRepository = auditOutboxRepository;
        this.redactionService = redactionService;
        this.forwarder = forwarder;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Admin mutations: part of the caller's transaction. Throws if the audit row cannot be written. */
    @Transactional
    public UUID recordInTransaction(AuditRecord record) {
        UUID id = persist(record);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    forwardQuietly(id);
                }
            });
        } else {
            forwardQuietly(id);
        }
        return id;
    }

    /**
     * Gateway / denial paths. Local durable write in its OWN transaction, then an async Kafka forward.
     * NEVER throws: if even the local write fails, a structured ERROR line (no payload) is the last resort.
     */
    public void recordNeverFail(AuditRecord record) {
        try {
            if (record == null || record.getTenantId() == null) {
                LOGGER.warn("CGI_AUDIT_SKIPPED no tenant on record (e.g. unauthenticated request): {}", record);
                return;
            }
            UUID id = requiresNewTemplate.execute(status -> persist(record));
            if (id != null) {
                forwardQuietly(id);
            }
        } catch (Throwable t) {
            LOGGER.error("CGI_AUDIT_LOCAL_WRITE_FAILED action={} outcome={} tenant={} errorClass={}",
                    record == null ? null : record.getAction(), record == null ? null : record.getOutcome(),
                    record == null ? null : record.getTenantId(), t.getClass().getName());
        }
    }

    private UUID persist(AuditRecord record) {
        // Redaction happens HERE, before anything is persisted or can reach Kafka.
        AuditEvent event = new AuditEvent(UUID.randomUUID(), record.getTenantId(), record.getActorId(),
                clip(record.getAction(), 64), clip(record.getTargetType(), 64), clip(record.getTargetId(), 255),
                clip(record.getOutcome(), 16), clock.instant(), redactionService.redactMap(record.getPayload()));
        auditEventRepository.save(event);
        auditOutboxRepository.save(new AuditOutbox(event.getId(), event.getTenantId(), Instant.now(clock), null, 0, null));
        return event.getId();
    }

    private void forwardQuietly(UUID id) {
        try {
            forwarder.forwardAsync(id);
        } catch (RuntimeException e) {
            // The event is already durable in the outbox; the scheduled retry will publish it.
            LOGGER.warn("Audit forward could not be scheduled ({}); retry job will pick it up", e.getClass().getSimpleName());
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "n/a";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
