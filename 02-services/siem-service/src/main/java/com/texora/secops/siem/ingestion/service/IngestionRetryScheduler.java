package com.texora.secops.siem.ingestion.service;

import com.texora.secops.siem.domain.SiemIngestionLog;
import com.texora.secops.siem.domain.SiemIngestionSource;
import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import com.texora.secops.siem.repository.SiemIngestionLogRepository;
import com.texora.secops.siem.repository.SiemIngestionSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The "retry with backoff" half of Requirement 1: periodically re-attempts every
 * {@code RETRIED} row whose backoff window has elapsed. A row that keeps failing past
 * {@code max-retries} is moved to DLQ by {@code IngestionOrchestrationService} itself on the
 * next attempt - this scheduler never deletes or skips a row, only re-attempts it.
 */
@Component
public class IngestionRetryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionRetryScheduler.class);

    private final SiemIngestionLogRepository ingestionLogRepository;
    private final SiemIngestionSourceRepository ingestionSourceRepository;
    private final IngestionOrchestrationService orchestrationService;

    public IngestionRetryScheduler(SiemIngestionLogRepository ingestionLogRepository,
                                    SiemIngestionSourceRepository ingestionSourceRepository,
                                    IngestionOrchestrationService orchestrationService) {
        this.ingestionLogRepository = ingestionLogRepository;
        this.ingestionSourceRepository = ingestionSourceRepository;
        this.orchestrationService = orchestrationService;
    }

    @Scheduled(fixedDelayString = "${siem.ingestion.retry-scan-interval-ms:5000}")
    public void retryDueEvents() {
        List<SiemIngestionLog> due = ingestionLogRepository
                .findAllByStatusAndNextRetryAtLessThanEqual(IngestionLogStatus.RETRIED, Instant.now());
        if (due.isEmpty()) {
            return;
        }
        LOGGER.debug("Re-attempting {} ingestion log rows past their backoff window", due.size());
        for (SiemIngestionLog log : due) {
            Optional<SiemIngestionSource> source = ingestionSourceRepository.findByIdRaw(log.getIngestionSourceId());
            if (source.isEmpty()) {
                // The source was deregistered after the event arrived - cannot re-normalize
                // without knowing which profile to use. DLQ it explicitly rather than retrying
                // forever against a source that no longer exists.
                log.setStatus(IngestionLogStatus.DLQ);
                log.setErrorSummary("Ingestion source " + log.getIngestionSourceId() + " no longer registered");
                ingestionLogRepository.save(log);
                continue;
            }
            orchestrationService.attemptProcessing(log, source.get().getName());
        }
    }
}
