package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "qap_flaky_test_record", schema = "qap")
public class QapFlakyTestRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_count", nullable = false)
    private int runCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "flake_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal flakeRate;

    @Column(name = "quarantined", nullable = false)
    private boolean quarantined;

    @Column(name = "quarantined_at")
    private Instant quarantinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public QapFlakyTestRecord() {
        // required by JPA
    }

    public QapFlakyTestRecord(UUID id, UUID testCaseId, UUID tenantId,
                               int runCount, int failCount, BigDecimal flakeRate,
                               boolean quarantined, Instant updatedAt) {
        this.id = id;
        this.testCaseId = testCaseId;
        this.tenantId = tenantId;
        this.runCount = runCount;
        this.failCount = failCount;
        this.flakeRate = flakeRate;
        this.quarantined = quarantined;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTestCaseId() { return testCaseId; }
    public void setTestCaseId(UUID testCaseId) { this.testCaseId = testCaseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public int getRunCount() { return runCount; }
    public void setRunCount(int runCount) { this.runCount = runCount; }

    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }

    public BigDecimal getFlakeRate() { return flakeRate; }
    public void setFlakeRate(BigDecimal flakeRate) { this.flakeRate = flakeRate; }

    public boolean isQuarantined() { return quarantined; }
    public void setQuarantined(boolean quarantined) { this.quarantined = quarantined; }

    public Instant getQuarantinedAt() { return quarantinedAt; }
    public void setQuarantinedAt(Instant quarantinedAt) { this.quarantinedAt = quarantinedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QapFlakyTestRecord)) return false;
        QapFlakyTestRecord that = (QapFlakyTestRecord) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "QapFlakyTestRecord{id=" + id + ", testCaseId=" + testCaseId
               + ", flakeRate=" + flakeRate + ", quarantined=" + quarantined + "}";
    }
}
