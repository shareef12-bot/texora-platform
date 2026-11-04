package com.texora.qap.dto;

import java.time.Instant;
import java.util.UUID;

public class TestResultResponse {

    private UUID id;
    private UUID testRunId;
    private UUID testCaseId;
    private UUID tenantId;
    private String outcome;
    private String traceId;
    private long durationMs;
    private String redactedRequest;
    private String redactedResponse;
    private int httpStatus;
    private String correlatedService;
    private String correlatedEndpoint;
    private Integer correlatedHttpStatus;
    private String correlatedExceptionType;
    private Instant executedAt;

    public TestResultResponse() {}

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

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getRedactedRequest() { return redactedRequest; }
    public void setRedactedRequest(String redactedRequest) { this.redactedRequest = redactedRequest; }

    public String getRedactedResponse() { return redactedResponse; }
    public void setRedactedResponse(String redactedResponse) { this.redactedResponse = redactedResponse; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

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
}
