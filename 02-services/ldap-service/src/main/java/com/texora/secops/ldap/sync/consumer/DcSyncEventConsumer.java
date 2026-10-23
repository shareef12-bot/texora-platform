package com.texora.secops.ldap.sync.consumer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.sync.service.DirectoryReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code dc.sync.requested} Kafka events and delegates to
 * {@link DirectoryReconciliationService}.
 *
 * <p>Uses manual acknowledgement — the offset is committed only after the
 * reconciliation attempt has been recorded in the database (PENDING or otherwise).
 * If the engine is unreachable the message is still acknowledged but the
 * sync state is recorded as PENDING for the retry scheduler.
 */
@Component
public class DcSyncEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcSyncEventConsumer.class);

    private final DirectoryReconciliationService reconciliationService;
    private final LdapAuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    public DcSyncEventConsumer(DirectoryReconciliationService reconciliationService,
                                LdapAuditHelper auditHelper,
                                ObjectMapper objectMapper) {
        this.reconciliationService = reconciliationService;
        this.auditHelper = auditHelper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${ldap.kafka.topics.dc-sync-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDcSyncRequested(@Payload String payload,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment ack) {
        LOGGER.info("Received dc.sync.requested event on topic={} partition={} offset={}", topic, partition, offset);

        DcSyncEvent event = null;
        try {
            event = objectMapper.readValue(payload, DcSyncEvent.class);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize dc.sync.requested payload at offset={}: {}", offset, payload, e);
            // Acknowledge so we don't get stuck on a poison pill
            ack.acknowledge();
            return;
        }

        UUID tenantId = event.getTenantId();
        String entryDn = event.getEntryDn();

        try {
            reconciliationService.reconcileEntry(tenantId, entryDn, event.getRequestedBy());
        } catch (Exception e) {
            // Log but do not re-throw; sync state is recorded as PENDING for retry scheduler
            LOGGER.error("Reconciliation failed for dn='{}' tenant={} — will retry via scheduler",
                    entryDn, tenantId, e);
            auditHelper.record(tenantId, event.getRequestedBy(),
                    "DC_SYNC_FAILED", "LdapSyncState", entryDn,
                    LdapAuditEvent.Outcome.FAILURE, null);
        } finally {
            ack.acknowledge();
        }
    }

    // ---------------------------------------------------------------------------
    // Inner event record (avoids a separate shared DTO with uncertain class name)
    // ---------------------------------------------------------------------------

    public static class DcSyncEvent {

        @JsonProperty("tenantId")
        private UUID tenantId;

        @JsonProperty("entryDn")
        private String entryDn;

        @JsonProperty("requestedBy")
        private UUID requestedBy;

        @JsonProperty("requestedAt")
        private String requestedAt;

        public DcSyncEvent() {
        }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        public String getEntryDn() { return entryDn; }
        public void setEntryDn(String entryDn) { this.entryDn = entryDn; }
        public UUID getRequestedBy() { return requestedBy; }
        public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
        public String getRequestedAt() { return requestedAt; }
        public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
    }
}
