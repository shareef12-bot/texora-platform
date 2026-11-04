package com.texora.qap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "qap_test_result", schema = "qap")
public class QapTestResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "test_run_id", nullable = false)
    private UUID testRunId;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;   // "PASS" | "FAIL" | "FLAKY" | "INCONCLUSIVE"

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "http_status")
    private int httpStatus;

    @Column(name = "screenshot_ref", length = 1024)
    private String screenshotRef;  // object-storage path only

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redacted_request", columnDefinition = "jsonb")
    private String redactedRequest;  // pre-redacted before persistence

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redacted_response", columnDefinition = "jsonb")
    private String redactedResponse; // pre-redacted before persistence

    @Column(name = "trace_id", length = 64)
    private String traceId;  // OpenTelemetry trace ID of the failing request

    // Filled by ObservabilityCorrelationClient after failure
    @Column(name = "correlated_service", length = 255)
    private String correlatedService;

    @Column(name = "correlated_endpoint", length = 512)
    private String correlatedEndpoint;

    @Column(name = "correlated_http_status")
    private Integer correlatedHttpStatus;

    @Column(name = "correlated_exception", columnDefinition = "TEXT")
    private String correlatedExceptionType;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public QapTestResult() {}

    public QapTestResult(UUID id, UUID testRunId, UUID testCaseId, String outcome,
                         long durationMs, Instant executedAt) {
        this.id = id;
        this.testRunId = testRunId;
        this.testCaseId = testCaseId;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.executedAt = executedAt;
        this.recordedAt = Instant.now();
    }

    // ---- Getters and Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTestRunId() { return testRunId; }
    public void setTestRunId(UUID testRunId) { this.testRunId = testRunId; }

    public UUID getTestCaseId() { return testCaseId; }
    public void setTestCaseId(UUID testCaseId) { this.testCaseId = testCaseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public String getScreenshotRef() { return screenshotRef; }
    public void setScreenshotRef(String screenshotRef) { this.screenshotRef = screenshotRef; }

    public String getRedactedRequest() { return redactedRequest; }
    public void setRedactedRequest(String redactedRequest) { this.redactedRequest = redactedRequest; }

    public String getRedactedResponse() { return redactedResponse; }
    public void setRedactedResponse(String redactedResponse) { this.redactedResponse = redactedResponse; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getCorrelatedService() { return correlatedService; }
    public void setCorrelatedService(String correlatedService) { this.correlatedService = correlatedService; }

    public String getCorrelatedEndpoint() { return correlatedEndpoint; }
    public void setCorrelatedEndpoint(String correlatedEndpoint) { this.correlatedEndpoint = correlatedEndpoint; }

    public Integer getCorrelatedHttpStatus() { return correlatedHttpStatus; }
    public void setCorrelatedHttpStatus(Integer correlatedHttpStatus) { this.correlatedHttpStatus = correlatedHttpStatus; }

    public String getCorrelatedExceptionType() { return correlatedExceptionType; }
    public void setCorrelatedExceptionType(String correlatedExceptionType) { this.correlatedExceptionType = correlatedExceptionType; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    // ---- equals / hashCode / toString ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QapTestResult)) return false;
        QapTestResult that = (QapTestResult) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QapTestResult{id=" + id + ", outcome='" + outcome + "', tenantId=" + tenantId + "}";
    }
}
