package com.texora.secops.cgi.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.texora.secops.cgi.audit.domain.AuditEvent;
import com.texora.secops.cgi.audit.domain.AuditOutbox;
import com.texora.secops.cgi.config.props.AuditProperties;
import com.texora.secops.cgi.repository.AuditEventRepository;
import com.texora.secops.cgi.repository.AuditOutboxRepository;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Forwards already-durable, already-redacted audit events to Kafka ({@code cgi.audit.events}) for SIEM.
 *
 * <p>NEVER on the request thread: {@link #forwardAsync} runs on the bounded {@code auditForwardExecutor}; if that
 * queue is full the event simply waits in {@code cgi_audit_outbox} for {@link #retryPending()}. A Kafka/SIEM outage
 * therefore degrades observability only. Uses its own private producer (not a shared bean) so it cannot interfere
 * with any KafkaTemplate the shared Audit SDK may register.
 */
@Component
public class AuditForwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditForwarder.class);

    private final KafkaOperations<String, String> kafka;
    private final KafkaTemplate<String, String> ownedTemplate;
    private final AuditProperties properties;
    private final AuditEventRepository events;
    private final AuditOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public AuditForwarder(KafkaProperties kafkaProperties, AuditProperties properties, AuditEventRepository events,
                          AuditOutboxRepository outbox, ObjectMapper objectMapper, PlatformTransactionManager tm, Clock clock) {
        this.ownedTemplate = buildTemplate(kafkaProperties, properties);
        this.kafka = ownedTemplate;
        this.properties = properties;
        this.events = events;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
    }

    /** Test seam. */
    AuditForwarder(KafkaOperations<String, String> kafka, AuditProperties properties, AuditEventRepository events,
                   AuditOutboxRepository outbox, ObjectMapper objectMapper, PlatformTransactionManager tm, Clock clock) {
        this.ownedTemplate = null;
        this.kafka = kafka;
        this.properties = properties;
        this.events = events;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
    }

    private static KafkaTemplate<String, String> buildTemplate(KafkaProperties kafkaProperties, AuditProperties properties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties((SslBundles) null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        int sendTimeout = Math.max(properties.getSendTimeoutMs(), 500);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeout);          // metadata fetch can never hang a thread
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, sendTimeout);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, sendTimeout * 2);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    /** Fire-and-forget. Must never throw into the caller. */
    @Async("auditForwardExecutor")
    public void forwardAsync(UUID auditEventId) {
        try {
            forwardOne(auditEventId);
        } catch (RuntimeException e) {
            LOGGER.warn("Audit forward failed unexpectedly ({}); retry job will pick it up", e.getClass().getSimpleName());
        }
    }

    /** Safety net: publishes anything the first attempt did not manage (Kafka was down, queue was full, pod died). */
    @Scheduled(fixedDelayString = "${cgi.audit.retry-interval-ms:30000}")
    public void retryPending() {
        try {
            Instant cutoff = clock.instant().minusMillis(properties.getRetryMinAgeMs());
            List<AuditOutbox> pending = outbox
                    .findTop100ByPublishedAtIsNullAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(
                            properties.getMaxForwardAttempts(), cutoff);
            for (AuditOutbox row : pending) {
                if (!forwardOne(row.getId())) {
                    break; // Kafka is still unhealthy: stop and try again next tick instead of hammering it
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Audit retry pass failed ({})", e.getClass().getSimpleName());
        }
    }

    /** @return true if the event is now published (or nothing to do); false if the attempt failed. */
    public boolean forwardOne(UUID auditEventId) {
        AuditOutbox row = outbox.findById(auditEventId).orElse(null);
        if (row == null || row.getPublishedAt() != null) {
            return true;
        }
        AuditEvent event = events.findByIdAndTenantId(auditEventId, row.getTenantId()).orElse(null);
        if (event == null) {
            LOGGER.error("Outbox row {} has no audit event - integrity problem", auditEventId);
            markAttempt(auditEventId, false, "MissingEvent");
            return false;
        }
        try {
            String message = toMessage(event);
            kafka.send(properties.getTopic(), event.getTenantId().toString(), message)
                    .get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            markAttempt(auditEventId, true, null);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markAttempt(auditEventId, false, "Interrupted");
            return false;
        } catch (Exception e) {
            // Class name only: broker error text may be verbose and is of no use in the outbox.
            markAttempt(auditEventId, false, e.getClass().getSimpleName());
            return false;
        }
    }

    private String toMessage(AuditEvent event) throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("eventId", event.getId().toString());
        node.put("tenantId", event.getTenantId().toString());
        if (event.getActorId() != null) {
            node.put("actorId", event.getActorId().toString());
        }
        node.put("action", event.getAction());
        node.put("targetType", event.getTargetType());
        node.put("targetId", event.getTargetId());
        node.put("outcome", event.getOutcome());
        node.put("occurredAt", event.getOccurredAt().toString());
        node.set("payload", objectMapper.valueToTree(event.getPayload())); // already redacted at write time
        node.put("source", "cgi-service");
        return objectMapper.writeValueAsString(node);
    }

    private void markAttempt(UUID id, boolean published, String error) {
        try {
            tx.executeWithoutResult(status -> outbox.findById(id).ifPresent(row -> {
                row.setAttempts(row.getAttempts() + 1);
                if (published) {
                    row.setPublishedAt(clock.instant());
                    row.setLastError(null);
                } else {
                    row.setLastError(error);
                }
                outbox.save(row);
            }));
        } catch (RuntimeException e) {
            LOGGER.warn("Could not update outbox state ({})", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void close() {
        if (ownedTemplate != null) {
            ownedTemplate.destroy();
        }
    }
}
