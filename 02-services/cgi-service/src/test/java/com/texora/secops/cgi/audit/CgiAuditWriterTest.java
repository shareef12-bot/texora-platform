package com.texora.secops.cgi.audit;

import com.texora.secops.cgi.audit.domain.AuditEvent;
import com.texora.secops.cgi.audit.service.AuditForwarder;
import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.audit.service.ParameterRedactionService;
import com.texora.secops.cgi.config.props.RedactionProperties;
import com.texora.secops.cgi.repository.AuditEventRepository;
import com.texora.secops.cgi.repository.AuditOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CgiAuditWriterTest {

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditOutboxRepository outbox = mock(AuditOutboxRepository.class);
    private final AuditForwarder forwarder = mock(AuditForwarder.class);
    private CgiAuditWriter writer;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        writer = new CgiAuditWriter(events, outbox, new ParameterRedactionService(new RedactionProperties(), new ObjectMapper()),
                forwarder, mock(PlatformTransactionManager.class), Clock.systemUTC());
    }

    private AuditRecord record(Map<String, Object> payload) {
        return AuditRecord.of(tenant, UUID.randomUUID(), "TEST", "ROUTE", "t-1", CgiAuditWriter.OUTCOME_SUCCESS, payload);
    }

    @Test
    void payloadIsRedactedBeforePersistence() {
        writer.recordNeverFail(record(Map.of("password", "hunter2", "ok", "fine")));
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(c.capture());
        assertThat(c.getValue().getPayload().toString()).doesNotContain("hunter2").contains("fine");
        verify(forwarder).forwardAsync(c.getValue().getId());
    }

    @Test
    void localWriteFailureNeverPropagatesOnTheGatewayPath() {
        doThrow(new IllegalStateException("db down")).when(events).save(any());
        writer.recordNeverFail(record(Map.of()));          // must not throw
        verify(forwarder, never()).forwardAsync(any());
    }

    @Test
    void forwarderFailureNeverPropagatesEither() {
        doThrow(new IllegalStateException("executor gone")).when(forwarder).forwardAsync(any());
        writer.recordNeverFail(record(Map.of()));          // must not throw
        verify(events).save(any());
    }

    @Test
    void recordWithoutTenantIsSkippedNotThrown() {
        writer.recordNeverFail(AuditRecord.of(null, null, "X", "Y", "z", "DENIED", Map.of()));
        writer.recordNeverFail(null);
        verify(events, never()).save(any());
    }

    @Test
    void adminPathPropagatesFailureSoNoMutationCommitsWithoutItsAuditRow() {
        doThrow(new IllegalStateException("db down")).when(events).save(any());
        assertThatThrownBy(() -> writer.recordInTransaction(record(Map.of()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminPathForwardsWhenNoTransactionSynchronizationIsActive() {
        writer.recordInTransaction(record(Map.of()));
        verify(forwarder).forwardAsync(any());
    }
}
