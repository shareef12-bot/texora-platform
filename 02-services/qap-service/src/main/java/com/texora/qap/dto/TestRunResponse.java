package com.texora.qap.dto;

import java.time.Instant;
import java.util.UUID;

public class TestRunResponse {

    private UUID id;
    private UUID suiteId;
    private UUID tenantId;
    private String status;
    private String triggeredBy;
    private Instant startedAt;
    private Instant completedAt;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private int inconclusiveCases;

    public TestRunResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSuiteId() { return suiteId; }
    public void setSuiteId(UUID suiteId) { this.suiteId = suiteId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }

    public int getPassedCases() { return passedCases; }
    public void setPassedCases(int passedCases) { this.passedCases = passedCases; }

    public int getFailedCases() { return failedCases; }
    public void setFailedCases(int failedCases) { this.failedCases = failedCases; }

    public int getInconclusiveCases() { return inconclusiveCases; }
    public void setInconclusiveCases(int inconclusiveCases) { this.inconclusiveCases = inconclusiveCases; }
}
