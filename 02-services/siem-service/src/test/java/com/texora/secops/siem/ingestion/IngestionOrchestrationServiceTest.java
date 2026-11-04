package com.texora.secops.siem.ingestion;

import com.texora.secops.siem.correlation.engine.RuleExecutionSandbox;
import com.texora.secops.siem.domain.SiemIngestionLog;
import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import com.texora.secops.siem.ingestion.metrics.IngestionMetrics;
import com.texora.secops.siem.ingestion.service.IngestionOrchestrationService;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.normalization.service.EventNormalizationService;
import com.texora.secops.siem.normalization.service.NormalizationException;
import com.texora.secops.siem.repository.SiemIngestionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQUIREMENT 1 - NEVER SILENTLY DROP AN EVENT. A row that keeps failing moves through
 *  BUFFERED -&gt; RETRIED -&gt; DLQ, is saved (never deleted) at every step. */
class IngestionOrchestrationServiceTest {

    private SiemIngestionLogRepository ingestionLogRepository;
    private EventNormalizationService normalizationService;
    private RuleExecutionSandbox ruleExecutionSandbox;
    private IngestionMetrics ingestionMetrics;
    private IngestionOrchestrationService service;

    @BeforeEach
    void setUp() {
        ingestionLogRepository = mock(SiemIngestionLogRepository.class);
        normalizationService = mock(EventNormalizationService.class);
        ruleExecutionSandbox = mock(RuleExecutionSandbox.class);
        ingestionMetrics = mock(IngestionMetrics.class);

        // maxRetries=2 so the test can exercise both the RETRIED and the DLQ branch quickly.
        service = new IngestionOrchestrationService(ingestionLogRepository, normalizationService,
                ruleExecutionSandbox, ingestionMetrics, 2, 10L, 100L);

        when(ingestionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SiemIngestionLog freshLog() {
        return new SiemIngestionLog(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), IngestionLogStatus.BUFFERED, 0, null, null, "{\"id\":\"e1\"}");
    }

    @Test
    void aPersistentlyFailingEventMovesToDlqRatherThanBeingDropped() throws Exception {
        when(normalizationService.normalizeAndIndex(anyString(), anyString(), any()))
                .thenThrow(new NormalizationException("malformed payload"));

        SiemIngestionLog log = freshLog();

        service.attemptProcessing(log, "ldap"); // attempt 1 of 2
        assertThat(log.getStatus()).isEqualTo(IngestionLogStatus.RETRIED);
        assertThat(log.getAttemptCount()).isEqualTo(1);

        service.attemptProcessing(log, "ldap"); // attempt 2 of 2 - exhausted
        assertThat(log.getStatus()).isEqualTo(IngestionLogStatus.DLQ);
        assertThat(log.getAttemptCount()).isEqualTo(2);

        // Never deleted at any point - the row remains queryable in DLQ.
        verify(ingestionLogRepository, never()).delete(any());
        verify(ingestionLogRepository, never()).deleteById(any());
    }

    @Test
    void aSuccessfulEventIsMarkedProcessed() throws Exception {
        NormalizedEvent normalized = new NormalizedEvent();
        normalized.setId(UUID.randomUUID().toString());
        when(normalizationService.normalizeAndIndex(anyString(), anyString(), any())).thenReturn(normalized);

        SiemIngestionLog log = freshLog();
        service.attemptProcessing(log, "ldap");

        assertThat(log.getStatus()).isEqualTo(IngestionLogStatus.PROCESSED);
        verify(ruleExecutionSandbox).evaluateAll(normalized);
    }
}
