package com.texora.secops.ftp.job.event;

import java.util.Objects;
import java.util.UUID;

public class QuarantineDestroyedEvent {

    private UUID tenantId;

    private UUID jobId;

    public QuarantineDestroyedEvent() {
    }

    public QuarantineDestroyedEvent(UUID tenantId, UUID jobId) {
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
        if (!(other instanceof QuarantineDestroyedEvent)) {
            return false;
        }
        QuarantineDestroyedEvent that = (QuarantineDestroyedEvent) other;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, jobId);
    }

    @Override
    public String toString() {
        return "QuarantineDestroyedEvent{" + "tenantId=" + tenantId + ", jobId=" + jobId + "}";
    }
}
