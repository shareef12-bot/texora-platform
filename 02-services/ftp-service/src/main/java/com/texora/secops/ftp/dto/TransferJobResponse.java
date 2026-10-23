package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TransferJobResponse {

    private UUID jobId;

    private String status;

    private UUID sourceEndpointId;

    private UUID destEndpointId;

    private String fileName;

    private boolean requireInspection;

    private UUID initiator;

    private String failureReason;

    private Instant createdAt;

    private Instant updatedAt;

    private List<TransferAttemptResponse> attempts;

    public TransferJobResponse() {
    }

    public TransferJobResponse(UUID jobId, String status, UUID sourceEndpointId, UUID destEndpointId, String fileName, boolean requireInspection, UUID initiator, String failureReason, Instant createdAt, Instant updatedAt, List<TransferAttemptResponse> attempts) {
        this.jobId = jobId;
        this.status = status;
        this.sourceEndpointId = sourceEndpointId;
        this.destEndpointId = destEndpointId;
        this.fileName = fileName;
        this.requireInspection = requireInspection;
        this.initiator = initiator;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attempts = attempts;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSourceEndpointId() {
        return sourceEndpointId;
    }

    public void setSourceEndpointId(UUID sourceEndpointId) {
        this.sourceEndpointId = sourceEndpointId;
    }

    public UUID getDestEndpointId() {
        return destEndpointId;
    }

    public void setDestEndpointId(UUID destEndpointId) {
        this.destEndpointId = destEndpointId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isRequireInspection() {
        return requireInspection;
    }

    public void setRequireInspection(boolean requireInspection) {
        this.requireInspection = requireInspection;
    }

    public UUID getInitiator() {
        return initiator;
    }

    public void setInitiator(UUID initiator) {
        this.initiator = initiator;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TransferAttemptResponse> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<TransferAttemptResponse> attempts) {
        this.attempts = attempts;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferJobResponse)) {
            return false;
        }
        TransferJobResponse that = (TransferJobResponse) other;
        return Objects.equals(jobId, that.jobId) && Objects.equals(status, that.status) && Objects.equals(sourceEndpointId, that.sourceEndpointId) && Objects.equals(destEndpointId, that.destEndpointId) && Objects.equals(fileName, that.fileName) && Objects.equals(requireInspection, that.requireInspection) && Objects.equals(initiator, that.initiator) && Objects.equals(failureReason, that.failureReason) && Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt) && Objects.equals(attempts, that.attempts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, status, sourceEndpointId, destEndpointId, fileName, requireInspection, initiator, failureReason, createdAt, updatedAt, attempts);
    }

    @Override
    public String toString() {
        return "TransferJobResponse{" + "jobId=" + jobId + ", status=" + status + ", sourceEndpointId=" + sourceEndpointId + ", destEndpointId=" + destEndpointId + ", fileName=" + fileName + ", requireInspection=" + requireInspection + ", initiator=" + initiator + ", failureReason=" + failureReason + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", attempts=" + attempts + "}";
    }
}
