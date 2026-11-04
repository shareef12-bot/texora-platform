package com.texora.secops.dc.sync.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.dc.domain.DcSyncJob;
import com.texora.secops.dc.infra.TimeSource;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The ONLY class that speaks to the LDAP Directory service (the isolation seam, B.4 spirit). The transport is the
 * Kafka topic dc.sync.requested; DC never performs LDAP bind/search (HLD Guardrail #3).
 *
 * <p>Envelope contract for the LDAP consumer (also in README):
 * <ul>
 *   <li>messageId = dc_sync_job.id - stable across retries; the consumer MUST de-duplicate on it (idempotency).</li>
 *   <li>issuedAt + nonce change on every (re)publish; the consumer MUST reject messages older than its replay window.</li>
 *   <li>signature = base64(HMAC) over canonical(version, messageId, tenantId, entityType, entityId, operation,
 *       issuedAt, nonce, payload) joined by '\n'; the consumer MUST verify it before applying anything.</li>
 * </ul>
 * Publish is synchronous (broker-acknowledged); callers invoke it from the publish executor / sweeper only.
 */
@Component
public class LdapControlPlaneClient {

    public static final int ENVELOPE_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SyncEnvelopeSigner signer;
    private final TimeSource time;
    private final String syncTopic;
    private final String dlqTopic;

    public LdapControlPlaneClient(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                  SyncEnvelopeSigner signer, TimeSource time,
                                  @Value("${dc.topics.sync:dc.sync.requested}") String syncTopic,
                                  @Value("${dc.topics.sync-dlq:dc.sync.requested.dlq}") String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.time = time;
        this.syncTopic = syncTopic;
        this.dlqTopic = dlqTopic;
    }

    public SyncEnvelope toEnvelope(DcSyncJob job) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(job.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize sync payload for job " + job.getId(), e);
        }
        SyncEnvelope envelope = new SyncEnvelope();
        envelope.setVersion(ENVELOPE_VERSION);
        envelope.setMessageId(job.getId().toString());
        envelope.setTenantId(job.getTenantId().toString());
        envelope.setEntityType(job.getEntityType());
        envelope.setEntityId(job.getEntityId());
        envelope.setOperation(job.getOperation());
        envelope.setIssuedAt(time.now().toString());
        envelope.setNonce(UUID.randomUUID().toString());
        envelope.setPayload(payloadJson);
        envelope.setKeyId(signer.keyId());
        envelope.setSignature(signer.sign(canonical(envelope)));
        return envelope;
    }

    /** Blocks until the broker acknowledges (or throws). */
    public void publish(SyncEnvelope envelope) throws Exception {
        send(syncTopic, envelope, null);
    }

    public void publishToDeadLetter(SyncEnvelope envelope, String reason) throws Exception {
        send(dlqTopic, envelope, reason);
    }

    static String canonical(SyncEnvelope e) {
        return String.join("\n", String.valueOf(e.getVersion()), e.getMessageId(), e.getTenantId(), e.getEntityType(),
                String.valueOf(e.getEntityId()), e.getOperation(), e.getIssuedAt(), e.getNonce(), e.getPayload());
    }

    private void send(String topic, SyncEnvelope envelope, String failureReason) throws Exception {
        String key = envelope.getTenantId() + ":" + envelope.getEntityType() + ":" + envelope.getEntityId(); // per-entity ordering
        ProducerRecord<String, String> record =
                new ProducerRecord<String, String>(topic, key, objectMapper.writeValueAsString(envelope));
        record.headers().add("messageId", envelope.getMessageId().getBytes(StandardCharsets.UTF_8));
        if (failureReason != null) {
            String trimmed = failureReason.length() > 500 ? failureReason.substring(0, 500) : failureReason;
            record.headers().add("dlqReason", trimmed.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }
}
