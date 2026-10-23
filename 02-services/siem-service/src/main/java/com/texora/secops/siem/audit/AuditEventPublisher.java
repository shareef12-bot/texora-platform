package com.texora.secops.siem.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.siem.config.KafkaConfig;
import com.texora.secops.siem.domain.SiemAuditEvent;
import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.siem.repository.SiemAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Standing in for the org's shared Audit SDK inside this service: writes the immutable record to
 * {@code siem_audit_event} (the application DB role has no UPDATE/DELETE grant on that table -
 * see V1 migration) and emits the same event to the {@code siem.audit.events} Kafka topic (B.1).
 * Persistence happens first and is never skipped even if the Kafka publish fails, so a broker
 * outage cannot cause an audit gap in the durable record.
 */
@Component("siemAuditEventPublisher")
public class AuditEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final SiemAuditEventRepository auditEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuditEventPublisher(SiemAuditEventRepository auditEventRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(UUID tenantId, UUID actorId, String action, String targetType,
                         String targetId, AuditOutcome outcome, Object payload) {
        String payloadJson = serialize(payload);
        SiemAuditEvent event = new SiemAuditEvent(UUID.randomUUID(), tenantId, actorId, action,
                targetType, targetId, outcome, Instant.now(), payloadJson);

        auditEventRepository.save(event);

        try {
            kafkaTemplate.send(KafkaConfig.SIEM_AUDIT_TOPIC, tenantId.toString(), payloadJson == null
                    ? "{}" : buildKafkaEnvelope(event));
        } catch (Exception ex) {
            // The durable DB row above is the source of truth; a Kafka publish failure is
            // logged, not swallowed, but it must never roll back or block the audit write itself.
            LOGGER.error("Failed to publish audit event {} to Kafka topic {} - DB record is still durable",
                    event.getId(), KafkaConfig.SIEM_AUDIT_TOPIC, ex);
        }
    }

    private String buildKafkaEnvelope(SiemAuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            LOGGER.warn("Failed to serialize audit payload of type {} - storing null payload", payload.getClass());
            return null;
        }
    }
}
