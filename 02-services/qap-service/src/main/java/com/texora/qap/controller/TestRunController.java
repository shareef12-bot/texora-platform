package com.texora.qap.controller;

import com.texora.qap.domain.QapTestResult;
import com.texora.qap.domain.QapTestRun;
import com.texora.qap.dto.TestResultResponse;
import com.texora.qap.dto.TestRunResponse;
import com.texora.qap.dto.TriggerRunRequest;
import com.texora.qap.repository.QapTestResultRepository;
import com.texora.qap.repository.QapTestRunRepository;
import com.texora.qap.execution.service.TestRunOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "Test Runs", description = "Trigger and query test run executions")
public class TestRunController {

    private final TestRunOrchestrationService orchestrationService;
    private final QapTestRunRepository runRepository;
    private final QapTestResultRepository resultRepository;

    public TestRunController(TestRunOrchestrationService orchestrationService,
                             QapTestRunRepository runRepository,
                             QapTestResultRepository resultRepository) {
        this.orchestrationService = orchestrationService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    @PostMapping
    @Operation(summary = "Trigger a new test run")
    public ResponseEntity<TestRunResponse> triggerRun(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody TriggerRunRequest request) {
        QapTestRun run = orchestrationService.triggerRun(
                request.getSuiteId(), tenantId, request.getTriggeredBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(run));
    }

    @GetMapping
    @Operation(summary = "List runs for tenant")
    public ResponseEntity<Page<TestRunResponse>> listRuns(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapTestRun> page = runRepository.findByTenantId(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get a specific run")
    public ResponseEntity<TestRunResponse> getRun(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID runId) {
        QapTestRun run = runRepository.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        return ResponseEntity.ok(toResponse(run));
    }

    @GetMapping("/{runId}/results")
    @Operation(summary = "Get results for a run")
    public ResponseEntity<Page<TestResultResponse>> getResults(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID runId,
            Pageable pageable) {
        Page<QapTestResult> page = resultRepository.findByTestRunIdAndTenantId(runId, tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResultResponse));
    }

    // ---- Mappers ----

    private TestRunResponse toResponse(QapTestRun r) {
        TestRunResponse dto = new TestRunResponse();
        dto.setId(r.getId());
        dto.setSuiteId(r.getSuiteId());
        dto.setTenantId(r.getTenantId());
        dto.setStatus(r.getStatus());
        dto.setTriggeredBy(r.getTriggeredBy());
        dto.setStartedAt(r.getStartedAt());
        dto.setCompletedAt(r.getCompletedAt());
        dto.setTotalCases(r.getTotalCases());
        dto.setPassedCases(r.getPassedCases());
        dto.setFailedCases(r.getFailedCases());
        dto.setInconclusiveCases(r.getInconclusiveCases());
        return dto;
    }

    private TestResultResponse toResultResponse(QapTestResult r) {
        TestResultResponse dto = new TestResultResponse();
        dto.setId(r.getId());
        dto.setTestRunId(r.getTestRunId());
        dto.setTestCaseId(r.getTestCaseId());
        dto.setTenantId(r.getTenantId());
        dto.setOutcome(r.getOutcome());
        dto.setTraceId(r.getTraceId());
        dto.setDurationMs(r.getDurationMs());
        dto.setRedactedRequest(r.getRedactedRequest());
        dto.setRedactedResponse(r.getRedactedResponse());
        dto.setHttpStatus(r.getHttpStatus());
        dto.setCorrelatedService(r.getCorrelatedService());
        dto.setCorrelatedEndpoint(r.getCorrelatedEndpoint());
        dto.setCorrelatedHttpStatus(r.getCorrelatedHttpStatus());
        dto.setCorrelatedExceptionType(r.getCorrelatedExceptionType());
        dto.setExecutedAt(r.getExecutedAt());
        return dto;
    }
}
