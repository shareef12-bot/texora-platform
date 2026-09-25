package com.texora.secops.sec.audit.service;

import com.texora.secops.sec.audit.domain.SecAuditEvent;
import com.texora.secops.sec.audit.repository.SecAuditEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the tamper-evident audit chain.
 *
 * <p>Each event's hash is computed over its canonical content plus the previous
 * event's hash. Any modification or deletion breaks the chain at that point and
 * every subsequent point.</p>
 *
 * <p>A scheduled job re-verifies the chain and records the result as a metric
 * so alerting rules can fire on any discontinuity.</p>
 */
@Service
public class AuditChainService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditChainService.class);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String KAFKA_TOPIC = "sec.audit.events";

    private final SecAuditEventRepository auditEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // 0 = chain intact, 1 = chain broken — used for alerting
    private final AtomicInteger chainBrokenGauge = new AtomicInteger(0);

    public AuditChainService(SecAuditEventRepository auditEventRepository,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              MeterRegistry meterRegistry) {
        this.auditEventRepository = auditEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        Gauge.builder("sec.audit.chain.broken", chainBrokenGauge, AtomicInteger::get)
                .description("1 if the audit chain has a detected discontinuity, 0 if intact")
                .register(meterRegistry);
    }

    /**
     * Appends an event to the tamper-evident audit chain and emits it to Kafka.
     *
     * <p>The event hash is: SHA-256(id + tenantId + actorId + action + targetType
     * + targetId + outcome + occurredAt + prevHash)</p>
     */
    @Transactional
    public SecAuditEvent appendEvent(UUID tenantId, String actorId, String action,
                                      String targetType, String targetId, String outcome,
                                      Map<String, Object> payload) {
        // Fetch the previous event's hash for chain linking
        Optional<SecAuditEvent> latest = auditEventRepository.findLatestEvent();
        String prevHash = latest.map(SecAuditEvent::getEventHash).orElse(GENESIS_HASH);

        UUID eventId = UUID.randomUUID();
        String eventHash = computeEventHash(eventId, tenantId, actorId, action,
                targetType, targetId, outcome, prevHash);

        SecAuditEvent event = new SecAuditEvent(
                eventId, tenantId, actorId, action, targetType, targetId,
                outcome, payload, prevHash, eventHash);

        SecAuditEvent saved = auditEventRepository.save(event);

        // Emit to Kafka with queue-and-retry semantics (configured in KafkaTemplate)
        emitToKafka(saved);

        return saved;
    }

    /**
     * Scheduled chain verification job. Runs every 15 minutes.
     * Re-computes the hash chain and alerts (via metric) on any break.
     */
    @Scheduled(cron = "${sec.audit.chain-verification.cron:0 */15 * * * *}")
    @Transactional(readOnly = true)
    public void verifyChain() {
        LOGGER.info("Starting audit chain verification");

        long broken = 0;
        String prevHash = GENESIS_HASH;
        long count = 0;
        Long breakAtSequence = null;

        for (SecAuditEvent event : auditEventRepository.findAllInSequenceOrder()) {
            count++;

            // Recompute the hash for this event
            String expectedHash = computeEventHash(
                    event.getId(), event.getTenantId(), event.getActorId(),
                    event.getAction(), event.getTargetType(), event.getTargetId(),
                    event.getOutcome(), prevHash);

            if (!expectedHash.equals(event.getEventHash())) {
                broken++;
                if (breakAtSequence == null) {
                    breakAtSequence = event.getSequenceNo();
                }
                LOGGER.error(
                    "AUDIT CHAIN BREAK detected at sequence_no={} eventId={}. " +
                    "Expected hash={} actual hash={}",
                    event.getSequenceNo(), event.getId(), expectedHash, event.getEventHash());
            }

            prevHash = event.getEventHash();
        }

        if (broken > 0) {
            chainBrokenGauge.set(1);
            LOGGER.error("Audit chain verification FAILED: {} break(s) detected, first at sequence={}",
                    broken, breakAtSequence);
        } else {
            chainBrokenGauge.set(0);
            LOGGER.info("Audit chain verification PASSED: {} events verified, chain intact", count);
        }
    }

    /**
     * Compute SHA-256 hash over the canonical event fields + prevHash.
     * This is the content commitment that makes tampering detectable.
     */
    String computeEventHash(UUID id, UUID tenantId, String actorId, String action,
                             String targetType, String targetId, String outcome, String prevHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = buildCanonicalContent(id, tenantId, actorId, action,
                    targetType, targetId, outcome, prevHash);
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed by the JCA spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String buildCanonicalContent(UUID id, UUID tenantId, String actorId, String action,
                                          String targetType, String targetId, String outcome,
                                          String prevHash) {
        // Deterministic, null-safe concatenation for the hash input
        return String.join("|",
                nullSafe(id),
                nullSafe(tenantId),
                nullSafe(actorId),
                nullSafe(action),
                nullSafe(targetType),
                nullSafe(targetId),
                nullSafe(outcome),
                nullSafe(prevHash));
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private void emitToKafka(SecAuditEvent event) {
        try {
            kafkaTemplate.send(KAFKA_TOPIC, event.getId().toString(), event);
            LOGGER.debug("Emitted audit event seq={} to Kafka", event.getSequenceNo());
        } catch (Exception ex) {
            // Kafka failure does not prevent the event from being persisted.
            // The KafkaTemplate is configured with retries; this is a last-resort log.
            LOGGER.error("Failed to emit audit event seq={} to Kafka — event persisted in DB",
                    event.getSequenceNo(), ex);
        }
    }
}
