package com.texora.secops.siem.dto.ingestion;

import com.texora.secops.siem.domain.SiemIngestionLog;
import com.texora.secops.siem.domain.enums.IngestionLogStatus;

import java.time.Instant;
import java.util.UUID;

public class IngestionLogResponse {

    private UUID id;
    private UUID ingestionSourceId;
    private Instant receivedAt;
    private IngestionLogStatus status;
    private int attemptCount;
    private Instant nextRetryAt;
    private String errorSummary;

    public IngestionLogResponse() {
        // required for JSON serialization
    }

    public static IngestionLogResponse from(SiemIngestionLog log) {
        IngestionLogResponse response = new IngestionLogResponse();
        response.setId(log.getId());
        response.setIngestionSourceId(log.getIngestionSourceId());
        response.setReceivedAt(log.getReceivedAt());
        response.setStatus(log.getStatus());
        response.setAttemptCount(log.getAttemptCount());
        response.setNextRetryAt(log.getNextRetryAt());
        response.setErrorSummary(log.getErrorSummary());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
}
