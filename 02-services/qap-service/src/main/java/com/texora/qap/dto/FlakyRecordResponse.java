package com.texora.qap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class FlakyRecordResponse {

    private UUID id;
    private UUID testCaseId;
    private UUID tenantId;
    private int runCount;
    private int failCount;
    private BigDecimal flakeRate;
    private boolean quarantined;
    private Instant updatedAt;

    public FlakyRecordResponse() {}

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

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
