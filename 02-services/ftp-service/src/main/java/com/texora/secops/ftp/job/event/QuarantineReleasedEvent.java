package com.texora.secops.ftp.job.event;

import java.util.Objects;
import java.util.UUID;

public class QuarantineReleasedEvent {

    private UUID tenantId;

    private UUID jobId;

    private UUID reviewer;

    public QuarantineReleasedEvent() {
    }

    public QuarantineReleasedEvent(UUID tenantId, UUID jobId, UUID reviewer) {
        this.tenantId = tenantId;
        this.jobId = jobId;
        this.reviewer = reviewer;
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

    public UUID getReviewer() {
        return reviewer;
    }

    public void setReviewer(UUID reviewer) {
        this.reviewer = reviewer;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuarantineReleasedEvent)) {
            return false;
        }
        QuarantineReleasedEvent that = (QuarantineReleasedEvent) other;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(jobId, that.jobId) && Objects.equals(reviewer, that.reviewer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, jobId, reviewer);
    }

    @Override
    public String toString() {
        return "QuarantineReleasedEvent{" + "tenantId=" + tenantId + ", jobId=" + jobId + ", reviewer=" + reviewer + "}";
    }
}
