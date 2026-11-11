package com.texora.secops.ldap.sync.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ldap.sync.service.DirectoryReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DcSyncEventConsumer")
class DcSyncEventConsumerTest {

    @Mock private DirectoryReconciliationService reconciliationService;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private Acknowledgment ack;

    private final ObjectMapper mapper = new ObjectMapper();
    private DcSyncEventConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new DcSyncEventConsumer(reconciliationService, mapper, kafkaTemplate);
        ReflectionTestUtils.setField(consumer, "topic", "dc.sync.requested");
        ReflectionTestUtils.setField(consumer, "baseDn", "dc=texora,dc=internal");
    }

    private String message(int version, String operation, String username) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("username", username, "displayName", "Test User"));
        return mapper.writeValueAsString(Map.of(
                "version", version, "messageId", UUID.randomUUID().toString(),
                "tenantId", tenantId.toString(), "entityType", "USER",
                "entityId", entityId.toString(), "operation", operation, "payload", payload));
    }

    @Test
    @DisplayName("valid message: applies sync with the built DN and acknowledges")
    void validMessage_applied() throws Exception {
        consumer.onDcSyncRequested(message(1, "UPSERT", "jsmith"), 0L, ack);

        verify(reconciliationService).applySync(eq(tenantId),
                eq("uid=jsmith,ou=users,dc=texora,dc=internal"), eq(entityId), eq("UPSERT"),
                any(), any(), isNull());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("unknown version: sent to the dead-letter topic, never applied")
    void unknownVersion_deadLettered() throws Exception {
        when(kafkaTemplate.send(eq("dc.sync.requested.dlq"), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.onDcSyncRequested(message(2, "UPSERT", "jsmith"), 0L, ack);

        verify(reconciliationService, never()).applySync(any(), any(), any(), any(), any(), anyMap(), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("unsafe username (DN injection): rejected and dead-lettered")
    void unsafeUsername_rejected() throws Exception {
        when(kafkaTemplate.send(eq("dc.sync.requested.dlq"), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.onDcSyncRequested(message(1, "UPSERT", "x,ou=admins"), 0L, ack);

        verify(reconciliationService, never()).applySync(any(), any(), any(), any(), any(), anyMap(), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("dead-letter publish fails: message is NOT acknowledged (re-read, not lost)")
    void dlqFailure_notAcknowledged() throws Exception {
        when(kafkaTemplate.send(eq("dc.sync.requested.dlq"), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        consumer.onDcSyncRequested(message(2, "UPSERT", "jsmith"), 0L, ack);

        verify(ack, never()).acknowledge();
    }
}
