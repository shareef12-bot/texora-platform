package com.texora.secops.dc.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.dc.domain.DcAuditEvent;
import com.texora.secops.dc.domain.DcAuditPublication;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.AuditEventRepository;
import com.texora.secops.dc.repository.AuditPublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes DC audit events to Kafka topic dc.audit.events (consumed by SIEM) with a durable local record FIRST and
 * a scheduled retry for anything not yet published (KNOWN-INTEGRATION-PITFALLS #11 guarantee).
 */
@Component
public class DcAuditPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcAuditPublisher.class);
    private static final long LEASE_SECONDS = 60L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuditPublicationRepository publications;
    private final AuditEventRepository events;
    private final TransactionTemplate newTx;
    private final TimeSource time;
    private final String topic;
    private final int batchSize;

    public DcAuditPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                            AuditPublicationRepository publications, AuditEventRepository events,
                            @Qualifier("dcNewTxTemplate") TransactionTemplate newTx, TimeSource time,
                            @Value("${dc.topics.audit:dc.audit.events}") String topic,
                            @Value("${dc.audit.retry-batch-size:100}") int batchSize) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.publications = publications;
        this.events = events;
        this.newTx = newTx;
        this.time = time;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    public String toJson(DcAuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", event.getId().toString());
        body.put("tenantId", event.getTenantId().toString());
        body.put("actorId", event.getActorId().toString());
        body.put("action", event.getAction());
        body.put("targetType", event.getTargetType());
        body.put("targetId", event.getTargetId());
        body.put("outcome", event.getOutcome());
        body.put("occurredAt", event.getOccurredAt().toString());
        body.put("payload", event.getPayload());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize audit event " + event.getId(), e);
        }
    }

    /** Blocking send (call from the publish executor / sweeper thread, never the request thread). */
    public void publish(UUID eventId, UUID tenantId, String json) {
        try {
            kafkaTemplate.send(topic, tenantId.toString(), json).get(5, TimeUnit.SECONDS);
            markPublished(eventId, tenantId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(eventId, tenantId, e);
        } catch (Exception e) {
            markFailed(eventId, tenantId, e);
        }
    }

    @Scheduled(fixedDelayString = "${dc.audit.retry-interval-ms:15000}")
    public void retryUnpublished() {
        Instant now = time.now();
        List<DcAuditPublication> claimed = newTx.execute(status -> {
            List<DcAuditPublication> due = publications.claimDueAcrossTenants(now, batchSize);
            List<DcAuditPublication> copy = new ArrayList<DcAuditPublication>();
            for (DcAuditPublication p : due) {
                p.setNextAttemptAt(now.plusSeconds(LEASE_SECONDS)); // lease: if this pod dies, row is retried later
                publications.save(p);
                copy.add(p);
            }
            return copy;
        });
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        LOGGER.info("Retrying {} unpublished audit events", claimed.size());
        for (DcAuditPublication p : claimed) {
            try {
                DcAuditEvent event = events.findByTenantIdAndId(p.getTenantId(), p.getAuditEventId()).orElse(null);
                if (event != null) {
                    publish(event.getId(), event.getTenantId(), toJson(event));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Audit retry failed for event {}", p.getAuditEventId(), e);
            }
        }
    }

    private void markPublished(UUID eventId, UUID tenantId) {
        newTx.execute(status -> {
            DcAuditPublication p = publications.findByTenantIdAndAuditEventId(tenantId, eventId).orElse(null);
            if (p != null) {
                p.setPublishedAt(time.now());
                p.setAttempts(p.getAttempts() + 1);
                p.setLastError(null);
                publications.save(p);
            }
            return null;
        });
    }

    private void markFailed(UUID eventId, UUID tenantId, Exception cause) {
        LOGGER.warn("Audit publish failed for event {} (will retry): {}", eventId, cause.toString());
        try {
            newTx.execute(status -> {
                DcAuditPublication p = publications.findByTenantIdAndAuditEventId(tenantId, eventId).orElse(null);
                if (p != null) {
                    int attempts = p.getAttempts() + 1;
                    long delay = Math.min(600L, 5L * (1L << Math.min(attempts, 7)));
                    p.setAttempts(attempts);
                    p.setNextAttemptAt(time.now().plusSeconds(delay));
                    String msg = String.valueOf(cause.getMessage());
                    p.setLastError(msg.length() > 500 ? msg.substring(0, 500) : msg);
                    publications.save(p);
                }
                return null;
            });
        } catch (RuntimeException e) {
            LOGGER.error("Could not record audit publish failure for {}", eventId, e);
        }
    }
}
