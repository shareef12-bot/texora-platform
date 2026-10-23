package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Requirement 1 - NEVER SILENTLY DROP AN EVENT. One row per ingested event, tracking its
 * progress through buffer -> retry (with backoff) -> DLQ (on exhaustion) -> processed. Never
 * deleted on failure; DLQ rows remain queryable and are the basis of the DLQ-depth metric.
 */
@Entity
@Table(name = "siem_ingestion_log", schema = "siem")
public class SiemIngestionLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingestion_source_id", nullable = false)
    private UUID ingestionSourceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IngestionLogStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "error_summary")
    private String errorSummary;

    @Lob
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    protected SiemIngestionLog() {
        // required by JPA
    }

    public SiemIngestionLog(UUID id, UUID tenantId, UUID ingestionSourceId, Instant receivedAt,
                             IngestionLogStatus status, int attemptCount, Instant nextRetryAt,
                             String errorSummary, String rawPayload) {
        this.id = id;
        this.tenantId = tenantId;
        this.ingestionSourceId = ingestionSourceId;
        this.receivedAt = receivedAt;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.errorSummary = errorSummary;
        this.rawPayload = rawPayload;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getIngestionSourceId() {
        return ingestionSourceId;
    }

    public void setIngestionSourceId(UUID ingestionSourceId) {
        this.ingestionSourceId = ingestionSourceId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public IngestionLogStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionLogStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemIngestionLog)) {
            return false;
        }
        SiemIngestionLog that = (SiemIngestionLog) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemIngestionLog{id=" + id + ", status=" + status + ", attemptCount=" + attemptCount + "}";
    }
}
