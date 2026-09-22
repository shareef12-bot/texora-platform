package com.texora.secops.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;

/**
 * Generic, non-blocking Kafka publish primitive for services that manage
 * their own audit entity and table (e.g. sso-service's {@code SsoAuditEvent} /
 * {@code SsoAuditEventRepository}) and therefore don't use the SDK's own
 * {@link com.texora.secops.audit.domain.AuditEvent} /
 * {@link com.texora.secops.audit.repository.AuditEventRepository} /
 * {@link com.texora.secops.audit.kafka.AuditKafkaPublisher} pipeline.
 *
 * <p>Unlike {@link com.texora.secops.audit.kafka.AuditKafkaPublisher}, this
 * class is intentionally decoupled from any specific entity or table: the
 * caller supplies the topic, key, and already-serialised payload directly.
 *
 * <p><strong>Guarantee:</strong> {@link #publish} is {@code @Async} — it never
 * blocks the calling (request) thread, using the shared {@code auditTaskExecutor}.
 *
 * <p><strong>Not provided here:</strong> unlike {@code AuditKafkaPublisher},
 * this class has no scheduled retry-on-failure, because it has no repository
 * of its own to re-scan. A Kafka publish failure is logged at ERROR level and
 * otherwise dropped. If a caller's audit table needs "never silently drop"
 * guarantees on top of this, that caller is responsible for its own retry
 * (e.g. a scheduled job over its own repository, tracking a status column),
 * the same way {@code AuditKafkaPublisher} does for the SDK's own table.
 */
public class AuditSdk {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditSdk.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AuditSdk(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a pre-serialised payload to the given topic asynchronously.
     * Never blocks the calling thread; never throws back to the caller —
     * failures are logged, not propagated.
     *
     * @param topic   the Kafka topic to publish to (e.g. {@code "sso.audit.events"})
     * @param key     the Kafka record key (e.g. the audit event's ID)
     * @param payload the already-serialised (typically JSON) event payload
     */
    @Async("auditTaskExecutor")
    public void publish(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("Kafka publish failed for topic={} key={}: {}",
                    topic, key, ex.getMessage(), ex);
            } else {
                LOGGER.debug("Published to topic={} key={} partition={} offset={}",
                    topic, key,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}