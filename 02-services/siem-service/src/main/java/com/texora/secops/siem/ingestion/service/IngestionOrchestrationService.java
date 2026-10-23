package com.texora.secops.siem.ingestion.service;

import com.texora.secops.siem.domain.SiemIngestionLog;
import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import com.texora.secops.siem.ingestion.metrics.IngestionMetrics;
import com.texora.secops.siem.ingestion.model.RawIngestionEvent;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.normalization.service.EventNormalizationService;
import com.texora.secops.siem.correlation.engine.RuleExecutionSandbox;
import com.texora.secops.siem.repository.SiemIngestionLogRepository;
import com.texora.secops.siem.security.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * REQUIREMENT 1 - NEVER SILENTLY DROP AN EVENT. Pipeline steps 2-4: buffer -> normalize+index ->
 * correlate, with retry-with-backoff and DLQ-on-exhaustion for any failure past the durable
 * buffer write.
 *
 * <p>The durability boundary is deliberate: {@link #handle} persists the {@code BUFFERED} row
 * first and lets that persistence failure propagate (so the Kafka consumer does not ack and the
 * broker redelivers - natural backpressure under a database outage). Once the row is durably
 * saved, every subsequent failure (normalization, indexing, correlation) is caught here and
 * turned into a {@code RETRIED} or {@code DLQ} state transition instead of an exception, because
 * from that point on the event is already safely tracked and must never be re-thrown in a way
 * that could be mistaken for "not ingested".
 */
@Service
public class IngestionOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionOrchestrationService.class);

    private final SiemIngestionLogRepository ingestionLogRepository;
    private final EventNormalizationService eventNormalizationService;
    private final RuleExecutionSandbox ruleExecutionSandbox;
    private final IngestionMetrics ingestionMetrics;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public IngestionOrchestrationService(SiemIngestionLogRepository ingestionLogRepository,
                                          EventNormalizationService eventNormalizationService,
                                          RuleExecutionSandbox ruleExecutionSandbox,
                                          IngestionMetrics ingestionMetrics,
                                          @Value("${siem.ingestion.max-retries:5}") int maxRetries,
                                          @Value("${siem.ingestion.initial-backoff-ms:500}") long initialBackoffMs,
                                          @Value("${siem.ingestion.max-backoff-ms:30000}") long maxBackoffMs) {
        this.ingestionLogRepository = ingestionLogRepository;
        this.eventNormalizationService = eventNormalizationService;
        this.ruleExecutionSandbox = ruleExecutionSandbox;
        this.ingestionMetrics = ingestionMetrics;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /** Called by MultiProtocolIngestionAdapter for every authenticated event, on every protocol.
     *  Returns normally (allowing the caller to ack/commit) as long as the event is durably
     *  recorded - even if downstream processing ultimately DLQ's it. */
    public SiemIngestionLog handle(RawIngestionEvent event) {
        SiemIngestionLog log = new SiemIngestionLog(UUID.randomUUID(), CallerContext.PLATFORM_TENANT_ID,
                event.getIngestionSourceId(), event.getReceivedAt(), IngestionLogStatus.BUFFERED,
                0, null, null, event.getRawPayload());
        // Durable write first - if this throws (DB outage), we deliberately do not catch it here,
        // so the Kafka listener will not ack and the broker will redeliver (Requirement 1).
        ingestionLogRepository.save(log);

        attemptProcessing(log, event.getSourceKey());
        return log;
    }

    /** Also used by IngestionRetryScheduler to re-attempt a previously RETRIED row. */
    @Transactional
    public void attemptProcessing(SiemIngestionLog log, String sourceKey) {
        try {
            NormalizedEvent normalized = eventNormalizationService.normalizeAndIndex(
                    sourceKey, log.getRawPayload(), Instant.now());
            ruleExecutionSandbox.evaluateAll(normalized); // never throws - isolates its own failures

            log.setStatus(IngestionLogStatus.PROCESSED);
            log.setErrorSummary(null);
            ingestionLogRepository.save(log);
            ingestionMetrics.recordLag(Duration.between(log.getReceivedAt(), Instant.now()));
            ingestionMetrics.incrementProcessed();
        } catch (Exception ex) {
            handleFailure(log, ex);
        }
    }

    private void handleFailure(SiemIngestionLog log, Exception ex) {
        int attempt = log.getAttemptCount() + 1;
        log.setAttemptCount(attempt);
        log.setErrorSummary(truncate(ex.getMessage()));

        if (attempt >= maxRetries) {
            log.setStatus(IngestionLogStatus.DLQ);
            log.setNextRetryAt(null);
            ingestionMetrics.incrementDlq();
            LOGGER.error("Ingestion log {} moved to DLQ after {} attempts (source {}): {}",
                    log.getId(), attempt, log.getIngestionSourceId(), ex.getMessage());
        } else {
            long backoff = Math.min(initialBackoffMs * (1L << (attempt - 1)), maxBackoffMs);
            log.setStatus(IngestionLogStatus.RETRIED);
            log.setNextRetryAt(Instant.now().plusMillis(backoff));
            LOGGER.warn("Ingestion log {} failed attempt {} - retrying in {}ms: {}",
                    log.getId(), attempt, backoff, ex.getMessage());
        }
        ingestionLogRepository.save(log);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
