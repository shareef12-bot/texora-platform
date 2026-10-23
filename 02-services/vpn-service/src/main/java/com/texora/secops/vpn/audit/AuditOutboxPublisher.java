package com.texora.secops.vpn.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.AuditEvent;
import com.texora.secops.vpn.domain.AuditOutbox;
import com.texora.secops.vpn.repository.AuditOutboxRepository;
import com.texora.secops.vpn.repository.VpnAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Publishes audit events to Kafka (vpn.audit.events -> SIEM) with RETRY. A failed or timed-out send leaves the outbox
 * row unpublished and reschedules it; nothing is ever dropped (VPN_TDD 'Session Audit' failure handling).
 * Delivery is at-least-once: consumers dedupe on the event id (the Kafka message key's sibling field "id").
 */
@Component
public class AuditOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditOutboxPublisher.class);

    private final AuditOutboxRepository outbox;
    private final VpnAuditEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final VpnProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditOutboxPublisher(AuditOutboxRepository outbox, VpnAuditEventRepository events,
                                KafkaTemplate<String, String> kafka, VpnProperties properties,
                                ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.events = events;
        this.kafka = kafka;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${vpn.audit.publish-interval-ms:2000}")
    public void scheduledPublish() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        try {
            publishDue();
        } catch (RuntimeException e) {
            LOGGER.error("Audit outbox publish cycle failed; will retry", e);
        }
    }

    /** @return number of events published in this pass. */
    public int publishDue() {
        Instant now = clock.instant();
        List<AuditOutbox> due = outbox.findDue(now, PageRequest.of(0, properties.getAudit().getBatchSize()));
        int published = 0;
        for (AuditOutbox row : due) {
            Instant lease = now.plusSeconds(properties.getAudit().getClaimLeaseSeconds());
            if (outbox.claim(row.getId(), now, lease) != 1) {
                continue; // another replica has it
            }
            if (publishOne(row)) {
                published++;
            }
        }
        return published;
    }

    private boolean publishOne(AuditOutbox row) {
        Instant now = clock.instant();
        try {
            Optional<AuditEvent> event = events.findById(row.getAuditEventId());
            if (event.isEmpty()) {
                throw new IllegalStateException("audit event missing for outbox row");
            }
            kafka.send(properties.getAudit().getTopic(), event.get().getTenantId().toString(), toMessage(event.get()))
                    .get(properties.getAudit().getPublishTimeoutMs(), TimeUnit.MILLISECONDS);
            outbox.markPublished(row.getId(), clock.instant());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reschedule(row, now, e);
            return false;
        } catch (Exception e) {
            reschedule(row, now, e);
            return false;
        }
    }

    private void reschedule(AuditOutbox row, Instant now, Exception e) {
        LOGGER.warn("Audit publish failed (attempt {}), will retry: {}", row.getAttempts() + 1, e.getClass().getSimpleName());
        String error = e.getClass().getSimpleName();
        outbox.markFailed(row.getId(), now.plusSeconds(properties.getAudit().getRetryBackoffSeconds()), error);
    }

    private String toMessage(AuditEvent event) throws JsonProcessingException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", event.getId().toString());
        node.put("tenantId", event.getTenantId().toString());
        node.put("actorId", event.getActorId().toString());
        node.put("action", event.getAction());
        node.put("targetType", event.getTargetType());
        node.put("targetId", event.getTargetId());
        node.put("outcome", event.getOutcome());
        node.put("occurredAt", event.getOccurredAt().toString());
        node.set("payload", objectMapper.readTree(event.getPayload()));
        return objectMapper.writeValueAsString(node);
    }
}
