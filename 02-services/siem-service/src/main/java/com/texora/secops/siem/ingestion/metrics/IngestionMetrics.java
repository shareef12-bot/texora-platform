package com.texora.secops.siem.ingestion.metrics;

import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import com.texora.secops.siem.repository.SiemIngestionLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Non-functional requirement: "Monitor DLQ depth as a first-class alertable metric - the DLQ
 * must be watched, not merely present" and "Ingestion lag and DLQ depth as first-class
 * operational metrics." Both are exposed on /actuator/prometheus for the platform's standard
 * alerting stack to page on. The DLQ gauge queries the repository directly on every scrape so
 * it is always current - never a value that can go stale between updates.
 */
@Component
public class IngestionMetrics {

    private final Timer ingestionLagTimer;
    private final Counter processedCounter;
    private final Counter dlqCounter;

    public IngestionMetrics(SiemIngestionLogRepository ingestionLogRepository, MeterRegistry meterRegistry) {
        Gauge.builder("siem_ingestion_dlq_depth", ingestionLogRepository,
                        repo -> repo.countByStatus(IngestionLogStatus.DLQ))
                .description("Live count of siem_ingestion_log rows in DLQ status - never silently "
                        + "dropped, but must be watched and drained")
                .register(meterRegistry);

        this.ingestionLagTimer = Timer.builder("siem_ingestion_lag")
                .description("Time between an event's receipt and its successful processing")
                .register(meterRegistry);

        this.processedCounter = Counter.builder("siem_ingestion_processed_total")
                .description("Events successfully processed (normalized, indexed, correlated)")
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("siem_ingestion_dlq_total")
                .description("Events that exhausted retries and moved to DLQ")
                .register(meterRegistry);
    }

    public void recordLag(Duration lag) {
        ingestionLagTimer.record(lag);
    }

    public void incrementProcessed() {
        processedCounter.increment();
    }

    public void incrementDlq() {
        dlqCounter.increment();
    }
}
