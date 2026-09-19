package com.texora.secops.audit.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link AuditEvent} records to Kafka asynchronously.
 *
 * <p><strong>Guarantees:</strong>
 * <ol>
 *   <li>The local DB record is written synchronously by {@link com.texora.secops.audit.aspect.AuditingAspect}
 *       <em>before</em> this publisher is called — so the event is never lost.</li>
 *   <li>{@link #publishAsync} is annotated {@code @Async} — it never blocks
 *       the request thread (standard B.6 §4).</li>
 *   <li>On Kafka failure the DB record stays with {@code kafkaStatus = FAILED}.</li>
 *   <li>{@link #retryFailedEvents()} runs every 60 seconds and re-publishes
 *       PENDING/FAILED events. Events are never silently dropped.</li>
 * </ol>
 *
 * <p>Topic name: {@code {serviceName}.audit.events}
 */
public class AuditKafkaPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public AuditKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                AuditEventRepository repository,
                                ObjectMapper objectMapper,
                                String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository    = repository;
        this.objectMapper  = objectMapper;
        this.serviceName   = serviceName;
    }

    /**
     * Publishes a single audit event to Kafka asynchronously.
     * Runs in the platform's async executor — never on the request thread.
     */
    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishAsync(AuditEvent event) {
        doPublish(event);
    }

    /**
     * Scheduled retry: re-publishes all PENDING/FAILED audit events.
     * Runs every 60 seconds. Uses a new transaction per event to avoid
     * one failure rolling back all retries.
     */
    @Scheduled(fixedDelayString = "${texora.audit.retry-interval-ms:60000}")
    public void retryFailedEvents() {
        List<AuditEvent> unpublished = repository.findUnpublished();
        if (unpublished.isEmpty()) {
            return;
        }
        LOGGER.info("Audit retry: re-publishing {} unpublished events", unpublished.size());
        for (AuditEvent event : unpublished) {
            try {
                retryOne(event);
            } catch (Exception ex) {
                LOGGER.error("Audit retry failed for event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOne(AuditEvent event) {
        doPublish(event);
    }

    // -------------------------------------------------------------------------

    private void doPublish(AuditEvent event) {
        String topic = serviceName + ".audit.events";
        String key   = event.getTenantId().toString();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            LOGGER.error("Cannot serialise audit event {} — marking FAILED", event.getId(), ex);
            repository.updateKafkaStatus(event.getId(), AuditEvent.KAFKA_STATUS_FAILED);
            return;
        }

        CompletableFuture<SendResult<String, String>> future =
            kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("Kafka publish failed for audit event {} topic={}: {}",
                    event.getId(), topic, ex.getMessage());
                repository.updateKafkaStatus(event.getId(), AuditEvent.KAFKA_STATUS_FAILED);
            } else {
                LOGGER.debug("Audit event {} published to topic {} partition {} offset {}",
                    event.getId(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
                repository.updateKafkaStatus(event.getId(), AuditEvent.KAFKA_STATUS_PUBLISHED);
            }
        });
    }
}
