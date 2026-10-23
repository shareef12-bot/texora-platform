package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "qap_test_run", schema = "qap")
public class QapTestRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "suite_id", nullable = false)
    private UUID suiteId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "triggered_by", nullable = false, length = 16)
    private String triggeredBy;   // "CI" | "SCHEDULE" | "MANUAL"

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;   // "RUNNING" | "COMPLETED" | "FAILED" | "INCONCLUSIVE"

    @Column(name = "total_cases")
    private int totalCases;

    @Column(name = "passed_cases")
    private int passedCases;

    @Column(name = "failed_cases")
    private int failedCases;

    @Column(name = "inconclusive_cases")
    private int inconclusiveCases;

    public QapTestRun() {}

    public QapTestRun(UUID id, UUID suiteId, UUID tenantId, String triggeredBy,
                      Instant startedAt, String status) {
        this.id = id;
        this.suiteId = suiteId;
        this.tenantId = tenantId;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSuiteId() { return suiteId; }
    public void setSuiteId(UUID suiteId) { this.suiteId = suiteId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }

    public int getPassedCases() { return passedCases; }
    public void setPassedCases(int passedCases) { this.passedCases = passedCases; }

    public int getFailedCases() { return failedCases; }
    public void setFailedCases(int failedCases) { this.failedCases = failedCases; }

    public int getInconclusiveCases() { return inconclusiveCases; }
    public void setInconclusiveCases(int inconclusiveCases) { this.inconclusiveCases = inconclusiveCases; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QapTestRun)) return false;
        QapTestRun that = (QapTestRun) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "QapTestRun{id=" + id + ", suiteId=" + suiteId + ", status='" + status + "'}";
    }
}
