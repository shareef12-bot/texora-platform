package com.texora.qap.dto;

import java.time.Instant;
import java.util.UUID;

public class DefectResponse {

    private UUID id;
    private UUID tenantId;
    private UUID testCaseId;
    private String failureSignatureHash;
    private String issueTrackerRef;
    private int occurrenceCount;
    private Instant firstSeenAt;
    private Instant lastSeenAt;

    public DefectResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getTestCaseId() { return testCaseId; }
    public void setTestCaseId(UUID testCaseId) { this.testCaseId = testCaseId; }

    public String getFailureSignatureHash() { return failureSignatureHash; }
    public void setFailureSignatureHash(String failureSignatureHash) { this.failureSignatureHash = failureSignatureHash; }

    public String getIssueTrackerRef() { return issueTrackerRef; }
    public void setIssueTrackerRef(String issueTrackerRef) { this.issueTrackerRef = issueTrackerRef; }

    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
