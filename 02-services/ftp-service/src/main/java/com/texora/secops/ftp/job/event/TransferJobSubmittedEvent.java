package com.texora.secops.ftp.job.event;

import java.util.Objects;
import java.util.UUID;

public class TransferJobSubmittedEvent {

    private UUID tenantId;

    private UUID jobId;

    public TransferJobSubmittedEvent() {
    }

    public TransferJobSubmittedEvent(UUID tenantId, UUID jobId) {
        this.tenantId = tenantId;
        this.jobId = jobId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferJobSubmittedEvent)) {
            return false;
        }
        TransferJobSubmittedEvent that = (TransferJobSubmittedEvent) other;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, jobId);
    }

    @Override
    public String toString() {
        return "TransferJobSubmittedEvent{" + "tenantId=" + tenantId + ", jobId=" + jobId + "}";
    }
}
