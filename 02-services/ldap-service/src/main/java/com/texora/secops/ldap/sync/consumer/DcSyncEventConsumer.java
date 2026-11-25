package com.texora.secops.ldap.sync.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ldap.sync.service.DirectoryReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Consumes DC's sync envelope from dc.sync.requested. Fail-closed:
 * an invalid message goes to the dead-letter topic and is never applied;
 * the offset is committed only after the sync state is saved.
 */
@Component
public class DcSyncEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcSyncEventConsumer.class);
    private static final int SUPPORTED_VERSION = 1;
    private static final List<String> OPERATIONS = List.of("UPSERT", "DELETE");
    // Letters, digits, dot, underscore, hyphen only: blocks LDAP DN injection.
    private static final Pattern SAFE_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final DirectoryReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${ldap.kafka.topics.dc-sync-requested}")
    private String topic;

    @Value("${ldap.engine.base-dn:dc=texora,dc=internal}")
    private String baseDn;

    public DcSyncEventConsumer(DirectoryReconciliationService reconciliationService,
                               ObjectMapper objectMapper,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${ldap.kafka.topics.dc-sync-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDcSyncRequested(@Payload String raw,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment ack) {
        try {
            DcSyncEnvelope env = objectMapper.readValue(raw, DcSyncEnvelope.class);
            validate(env);
            Map<?, ?> p = objectMapper.readValue(env.getPayload(), Map.class);
            String username = p.get("username") == null ? "" : String.valueOf(p.get("username"));
            if (!SAFE_USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("unsafe or missing username");
            }
            String dn = "uid=" + username + ",ou=users," + baseDn;
            Map<String, List<String>> attrs = "DELETE".equals(env.getOperation())
                    ? Map.of() : reconciliationService.parseAttributes(env.getPayload());
            reconciliationService.applySync(env.getTenantId(), dn, env.getEntityId(),
                    env.getOperation(), env.getPayload(), attrs, null);
            ack.acknowledge();
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            LOGGER.error("Rejecting invalid sync message at offset={}: {}", offset, e.getMessage());
            try {
                kafkaTemplate.send(topic + ".dlq", raw).get(5, java.util.concurrent.TimeUnit.SECONDS);
                ack.acknowledge();
            } catch (Exception dlqFailure) {
                // Not acknowledged: the message is re-read rather than lost.
                LOGGER.error("Dead-letter publish failed at offset={}", offset, dlqFailure);
            }
        }
        // Any other exception (e.g. database down) is not acknowledged, so the message is re-read.
    }

    private void validate(DcSyncEnvelope e) {
        if (e.getVersion() == null || e.getVersion() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("unsupported version");
        }
        if (e.getTenantId() == null || e.getEntityId() == null || e.getPayload() == null) {
            throw new IllegalArgumentException("missing tenantId, entityId or payload");
        }
        if (!"USER".equals(e.getEntityType())) {
            throw new IllegalArgumentException("unsupported entityType");
        }
        if (!OPERATIONS.contains(e.getOperation())) {
            throw new IllegalArgumentException("unsupported operation");
        }
    }
}