package com.texora.secops.cgi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.audit.domain.AuditEvent;
import com.texora.secops.cgi.audit.domain.AuditOutbox;
import com.texora.secops.cgi.audit.service.AuditForwarder;
import com.texora.secops.cgi.config.props.AuditProperties;
import com.texora.secops.cgi.repository.AuditEventRepository;
import com.texora.secops.cgi.repository.AuditOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AuditForwarderTest {

    private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditOutboxRepository outbox = mock(AuditOutboxRepository.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final AuditProperties props = new AuditProperties();
    private AuditForwarder forwarder;
    private AuditOutbox row;

    @BeforeEach
    void setUp() throws Exception {
        props.setSendTimeoutMs(500);
        Constructor<AuditForwarder> ctor = AuditForwarder.class.getDeclaredConstructor(KafkaOperations.class, AuditProperties.class,
                AuditEventRepository.class, AuditOutboxRepository.class, ObjectMapper.class, PlatformTransactionManager.class, Clock.class);
        ctor.setAccessible(true);
        forwarder = ctor.newInstance(kafka, props, events, outbox, new ObjectMapper(), mock(PlatformTransactionManager.class), Clock.systemUTC());
        row = new AuditOutbox(id, tenant, Instant.now().minusSeconds(60), null, 0, null);
        AuditEvent ev = new AuditEvent(id, tenant, UUID.randomUUID(), "A", "ROUTE", "t", "SUCCESS", Instant.now(), Map.of("k", "v"));
        when(outbox.findById(id)).thenReturn(Optional.of(row));
        when(events.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(ev));
    }

    @Test
    void successfulSendMarksPublished() {
        SendResult<String, String> sent = mock(SendResult.class);
        CompletableFuture<SendResult<String, String>> done = CompletableFuture.completedFuture(sent);
        when(kafka.send(eq("cgi.audit.events"), eq(tenant.toString()), any())).thenReturn(done);
        assertThat(forwarder.forwardOne(id)).isTrue();
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void kafkaFailureLeavesEventUnpublishedAndNeverThrows() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafka.send(any(), any(), any())).thenReturn(failed);
        assertThat(forwarder.forwardOne(id)).isFalse();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getLastError()).isEqualTo("ExecutionException");
    }

    @Test
    void synchronousKafkaExceptionIsAlsoContained() {
        when(kafka.send(any(), any(), any())).thenThrow(new IllegalStateException("metadata timeout"));
        assertThat(forwarder.forwardOne(id)).isFalse();
        forwarder.forwardAsync(id);   // must not throw
    }

    @Test
    void alreadyPublishedOrUnknownRowsAreNoOps() {
        row.setPublishedAt(Instant.now());
        assertThat(forwarder.forwardOne(id)).isTrue();
        verify(kafka, never()).send(any(), any(), any());
        assertThat(forwarder.forwardOne(UUID.randomUUID())).isTrue();
    }

    @Test
    void retryStopsAtFirstFailureToAvoidHammeringKafka() {
        AuditOutbox other = new AuditOutbox(UUID.randomUUID(), tenant, Instant.now().minusSeconds(60), null, 0, null);
        when(outbox.findTop100ByPublishedAtIsNullAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(anyInt(), any()))
                .thenReturn(List.of(row, other));
        when(kafka.send(any(), any(), any())).thenThrow(new IllegalStateException("down"));
        forwarder.retryPending();
        verify(kafka, org.mockito.Mockito.times(1)).send(any(), any(), any());
    }

    @Test
    void retryPassSwallowsRepositoryErrors() {
        when(outbox.findTop100ByPublishedAtIsNullAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(anyInt(), any()))
                .thenThrow(new IllegalStateException("db down"));
        forwarder.retryPending();   // must not throw
    }
}
