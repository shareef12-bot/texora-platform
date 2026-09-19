package com.texora.secops.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AuditEventRepository repository;

    @Mock
    private SendResult<String, String> sendResult;

    private AuditKafkaPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new AuditKafkaPublisher(kafkaTemplate, repository, objectMapper, "test-service");
    }

    @Test
    void publishAsync_updates_status_to_PUBLISHED_on_success() {
        AuditEvent event = buildEvent();
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(sendResult);

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publishAsync(event);

        verify(repository, timeout(1000))
            .updateKafkaStatus(event.getId(), AuditEvent.KAFKA_STATUS_PUBLISHED);
    }

    @Test
    void publishAsync_updates_status_to_FAILED_on_kafka_error() {
        AuditEvent event = buildEvent();
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publishAsync(event);

        verify(repository, timeout(1000))
            .updateKafkaStatus(event.getId(), AuditEvent.KAFKA_STATUS_FAILED);
    }

    @Test
    void retryFailedEvents_republishes_pending_events() {
        AuditEvent event = buildEvent();
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(sendResult);

        when(repository.findUnpublished()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.retryFailedEvents();

        verify(kafkaTemplate).send(
            eq("test-service.audit.events"),
            eq(event.getTenantId().toString()),
            anyString()
        );
    }

    @Test
    void retryFailedEvents_does_nothing_when_no_unpublished_events() {
        when(repository.findUnpublished()).thenReturn(List.of());

        publisher.retryFailedEvents();

        verifyNoInteractions(kafkaTemplate);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private AuditEvent buildEvent() {
        return new AuditEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "USER_CREATED",
            "USER",
            UUID.randomUUID().toString(),
            AuditEvent.OUTCOME_SUCCESS,
            Instant.now(),
            null,
            "test-service"
        );
    }
}
