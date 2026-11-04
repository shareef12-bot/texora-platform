package com.texora.secops.dc.sync.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SyncJobResponse {

    private UUID id;

    private String source;

    private String entityType;

    private String status;

    private int recordsProcessed;

    private Instant startedAt;

    private Instant completedAt;

    private String errorSummary;

    public SyncJobResponse() {
        // required by Jackson
    }

    public SyncJobResponse(UUID id, String source, String entityType, String status, int recordsProcessed, Instant startedAt, Instant completedAt, String errorSummary) {
        this.id = id;
        this.source = source;
        this.entityType = entityType;
        this.status = status;
        this.recordsProcessed = recordsProcessed;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorSummary = errorSummary;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(int recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SyncJobResponse that = (SyncJobResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(source, that.source) && Objects.equals(entityType, that.entityType) && Objects.equals(status, that.status) && Objects.equals(recordsProcessed, that.recordsProcessed) && Objects.equals(startedAt, that.startedAt) && Objects.equals(completedAt, that.completedAt) && Objects.equals(errorSummary, that.errorSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, entityType, status, recordsProcessed, startedAt, completedAt, errorSummary);
    }

    @Override
    public String toString() {
        return "SyncJobResponse{id=" + id + ", " + "source=" + source + ", " + "entityType=" + entityType + ", " + "status=" + status + ", " + "recordsProcessed=" + recordsProcessed + ", " + "startedAt=" + startedAt + ", " + "completedAt=" + completedAt + ", " + "errorSummary=" + errorSummary + "}";
    }
}
