package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SubmitTransferJobResponse {

    private UUID jobId;

    private String status;

    private Instant createdAt;

    public SubmitTransferJobResponse() {
    }

    public SubmitTransferJobResponse(UUID jobId, String status, Instant createdAt) {
        this.jobId = jobId;
        this.status = status;
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubmitTransferJobResponse)) {
            return false;
        }
        SubmitTransferJobResponse that = (SubmitTransferJobResponse) other;
        return Objects.equals(jobId, that.jobId) && Objects.equals(status, that.status) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, status, createdAt);
    }

    @Override
    public String toString() {
        return "SubmitTransferJobResponse{" + "jobId=" + jobId + ", status=" + status + ", createdAt=" + createdAt + "}";
    }
}
