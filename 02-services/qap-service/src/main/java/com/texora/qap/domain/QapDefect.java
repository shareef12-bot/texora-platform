package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A deduplicated defect record. Multiple test failures that share the same
 * (endpoint + errorType + service) map to ONE QapDefect row via
 * failure_signature_hash + tenant_id (unique constraint in schema).
 * Idempotency requirement: re-running a failed suite increments occurrence_count
 * on the SAME row, never creates a new one.
 */
@Entity
@Table(name = "qap_defect", schema = "qap")
public class QapDefect {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "test_result_id", nullable = false)
    private UUID testResultId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "issue_tracker_ref", length = 256)
    private String issueTrackerRef;

    @Column(name = "failure_signature_hash", nullable = false, length = 64)
    private String failureSignatureHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "test_case_id")
    private UUID testCaseId;

    public QapDefect() {
        // required by JPA
    }

    public QapDefect(UUID id, UUID testResultId, UUID tenantId,
                     String failureSignatureHash, String status,
                     Instant firstSeenAt, Instant lastSeenAt, int occurrenceCount) {
        this.id = id;
        this.testResultId = testResultId;
        this.tenantId = tenantId;
        this.failureSignatureHash = failureSignatureHash;
        this.status = status;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.occurrenceCount = occurrenceCount;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTestResultId() { return testResultId; }
    public void setTestResultId(UUID testResultId) { this.testResultId = testResultId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getIssueTrackerRef() { return issueTrackerRef; }
    public void setIssueTrackerRef(String issueTrackerRef) { this.issueTrackerRef = issueTrackerRef; }

    public String getFailureSignatureHash() { return failureSignatureHash; }
    public void setFailureSignatureHash(String failureSignatureHash) { this.failureSignatureHash = failureSignatureHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public int getOccurrenceCount() { return occurrenceCount; }

    public UUID getTestCaseId() { return testCaseId; }
    public void setTestCaseId(UUID testCaseId) { this.testCaseId = testCaseId; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QapDefect)) return false;
        QapDefect that = (QapDefect) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "QapDefect{id=" + id + ", hash='" + failureSignatureHash + "', occurrences=" + occurrenceCount + "}";
    }
}
