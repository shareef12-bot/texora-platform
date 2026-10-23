package com.texora.secops.cgi.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Execution record (cgi_execution_record). exitReason is a short reason code, never handler output.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_execution_record", schema = "cgi")
public class ExecutionRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "request_log_id", nullable = false, updatable = false)
    private UUID requestLogId;

    @Column(name = "worker_id", nullable = false, length = 128, updatable = false)
    private String workerId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false, updatable = false)
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private ExecutionOutcome outcome;

    @Column(name = "exit_reason", nullable = true, length = 255, updatable = false)
    private String exitReason;

    protected ExecutionRecord() {
        // required by JPA
    }

    public ExecutionRecord(UUID id, UUID tenantId, UUID requestLogId, String workerId, Instant startedAt, Instant endedAt, ExecutionOutcome outcome, String exitReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestLogId = requestLogId;
        this.workerId = workerId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.outcome = outcome;
        this.exitReason = exitReason;
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

    public UUID getRequestLogId() {
        return requestLogId;
    }

    public void setRequestLogId(UUID requestLogId) {
        this.requestLogId = requestLogId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public ExecutionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ExecutionOutcome outcome) {
        this.outcome = outcome;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutionRecord)) {
            return false;
        }
        ExecutionRecord that = (ExecutionRecord) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExecutionRecord{" + "id=" + id + ", tenantId=" + tenantId + ", requestLogId=" + requestLogId + ", workerId=" + workerId + ", startedAt=" + startedAt + ", endedAt=" + endedAt + ", outcome=" + outcome + ", exitReason=" + exitReason + "}";
    }

}
