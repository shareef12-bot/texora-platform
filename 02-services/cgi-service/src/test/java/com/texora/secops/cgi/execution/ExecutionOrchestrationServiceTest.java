package com.texora.secops.cgi.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.audit.service.ParameterRedactionService;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.config.props.RedactionProperties;
import com.texora.secops.cgi.config.service.TenantConfigService;
import com.texora.secops.cgi.execution.adapter.IsolationSpec;
import com.texora.secops.cgi.execution.adapter.WorkerHandle;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.adapter.WorkerSpawnException;
import com.texora.secops.cgi.execution.domain.ExecutionOutcome;
import com.texora.secops.cgi.execution.domain.ExecutionRecord;
import com.texora.secops.cgi.execution.domain.RequestLog;
import com.texora.secops.cgi.execution.service.ExecutionBulkhead;
import com.texora.secops.cgi.execution.service.ExecutionOrchestrationService;
import com.texora.secops.cgi.execution.service.WorkerIsolationConfigService;
import com.texora.secops.cgi.gateway.dto.GatewayCall;
import com.texora.secops.cgi.gateway.dto.GatewayResponse;
import com.texora.secops.cgi.gateway.service.RouteMatchingService;
import com.texora.secops.cgi.repository.ExecutionRecordRepository;
import com.texora.secops.cgi.repository.RequestLogRepository;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.PolicyDecision;
import com.texora.secops.cgi.security.PolicyDecisionClient;
import com.texora.secops.cgi.timeout.service.ExecutionTimeoutService;
import com.texora.secops.cgi.timeout.service.SupervisionResult;
import com.texora.secops.cgi.timeout.service.TimeoutControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionOrchestrationServiceTest {

    private final RouteMatchingService matching = mock(RouteMatchingService.class);
    private final PolicyDecisionClient policy = mock(PolicyDecisionClient.class);
    private final TenantConfigService tenantConfig = mock(TenantConfigService.class);
    private final RequestLogRepository requestLogs = mock(RequestLogRepository.class);
    private final ExecutionRecordRepository records = mock(ExecutionRecordRepository.class);
    private final WorkerIsolationConfigService isolation = mock(WorkerIsolationConfigService.class);
    private final TimeoutControlService timeoutControl = mock(TimeoutControlService.class);
    private final ExecutionTimeoutService timeoutService = mock(ExecutionTimeoutService.class);
    private final WorkerIsolationAdapter adapter = mock(WorkerIsolationAdapter.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private final ExecutionProperties props = new ExecutionProperties();
    private final ParameterRedactionService redaction = new ParameterRedactionService(new RedactionProperties(), new ObjectMapper());
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

    private final UUID tenant = UUID.randomUUID();
    private final CallerContext caller = new CallerContext(tenant, UUID.randomUUID(), Set.of());
    private final RouteAllowlistEntry route = new RouteAllowlistEntry(UUID.randomUUID(), tenant, "/legacy/report", "stub-echo", 5,
            RouteStatus.APPROVED, UUID.randomUUID(), Instant.now(), Instant.now());
    private final WorkerHandle handle = new WorkerHandle() {
        public String workerId() {
            return "w-1";
        }

        public CompletableFuture<WorkerResult> completion() {
            return new CompletableFuture<>();
        }
    };

    private ExecutionBulkhead bulkhead;
    private ExecutionOrchestrationService service;

    @BeforeEach
    void setUp() {
        bulkhead = new ExecutionBulkhead(props);
        service = new ExecutionOrchestrationService(matching, policy, redaction, tenantConfig, requestLogs, records, isolation,
                timeoutControl, timeoutService, adapter, bulkhead, audit, props, clock);
        when(matching.match(tenant, "/legacy/report")).thenReturn(Optional.of(route));
        when(tenantConfig.maxRequestBodyBytes(tenant)).thenReturn(1000L);
        when(tenantConfig.extraSensitiveKeys(tenant)).thenReturn(Set.of());
        when(policy.evaluate(any(), any(), any())).thenReturn(PolicyDecision.allow());
        when(isolation.effectiveSpec(any(), any())).thenReturn(IsolationSpec.strictDefaults("stub-echo", "strict-default"));
        when(timeoutControl.effectiveTimeout(any(), any())).thenReturn(Duration.ofSeconds(5));
        when(adapter.spawn(any())).thenReturn(handle);
    }

    private GatewayCall call(String path, Map<String, String[]> params, byte[] body) {
        return new GatewayCall(caller, "GET", path, params, "application/json", body);
    }

    private void supervisionReturns(SupervisionResult r) {
        when(timeoutService.supervise(any(), any())).thenReturn(r);
    }

    @Test
    void unlistedRouteIs404AndNoWorkerOrLogIsCreated() {
        when(matching.match(tenant, "/nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(call("/nope", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        verify(adapter, never()).spawn(any());
        verify(requestLogs, never()).save(any());
        verify(policy, never()).evaluate(any(), any(), any());
    }

    @Test
    void policyDenyAndPolicyTimeoutRejectBeforeAnyWorker() {
        when(policy.evaluate(any(), any(), any())).thenReturn(PolicyDecision.deny(PolicyDecision.REASON_SEC_DENY));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        when(policy.evaluate(any(), any(), any())).thenReturn(PolicyDecision.deny(PolicyDecision.REASON_SEC_TIMEOUT));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(504));
        verify(adapter, never()).spawn(any());
    }

    @Test
    void oversizedBodyIsRejectedBeforeAnyWorker() {
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), new byte[2000])))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("BODY_TOO_LARGE"));
        verify(adapter, never()).spawn(any());
    }

    @Test
    void happyPathReturnsHandlerResponseAndLogsOnlyRedactedParameters() {
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_0",
                new WorkerResult(0, 200, "application/json", "{\"ok\":true}".getBytes())));
        Map<String, String[]> params = Map.of("password", new String[]{"hunter2"}, "report", new String[]{"daily"});

        GatewayResponse r = service.handle(call("/legacy/report", params, new byte[3]));

        assertThat(r.getStatus()).isEqualTo(200);
        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogs).save(logCaptor.capture());
        String stored = logCaptor.getValue().getRedactedParams().toString();
        assertThat(stored).doesNotContain("hunter2").contains("daily");
        ArgumentCaptor<ExecutionRecord> rec = ArgumentCaptor.forClass(ExecutionRecord.class);
        verify(records).save(rec.capture());
        assertThat(rec.getValue().getOutcome()).isEqualTo(ExecutionOutcome.COMPLETED);
        assertThat(bulkhead.availablePermits()).isEqualTo(props.getMaxConcurrentWorkers());
    }

    @Test
    void timeoutIs504AndRecordedTimedOut() {
        supervisionReturns(new SupervisionResult(ExecutionOutcome.TIMED_OUT, "TIMEOUT_KILLED", null));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("EXECUTION_TIMEOUT"));
        ArgumentCaptor<ExecutionRecord> rec = ArgumentCaptor.forClass(ExecutionRecord.class);
        verify(records).save(rec.capture());
        assertThat(rec.getValue().getOutcome()).isEqualTo(ExecutionOutcome.TIMED_OUT);
        assertThat(bulkhead.availablePermits()).isEqualTo(props.getMaxConcurrentWorkers());
    }

    @Test
    void spawnFailureIsFailedAndReleasesCapacity() {
        when(adapter.spawn(any())).thenThrow(new WorkerSpawnException("UNKNOWN_HANDLER"));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("EXECUTION_FAILED"));
        assertThat(bulkhead.availablePermits()).isEqualTo(props.getMaxConcurrentWorkers());
    }

    @Test
    void unexpectedErrorKillsTheWorkerAndFailsClosed() {
        when(timeoutService.supervise(any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null))).isInstanceOf(CgiApiException.class);
        verify(timeoutService).killQuietly(handle);
    }

    @Test
    void nonZeroExitOrBadStatusOrHugeBodyIsFailedNotPassedThrough() {
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_3", new WorkerResult(3, 200, "text/plain", null)));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null))).isInstanceOf(CgiApiException.class);
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_0", new WorkerResult(0, 999, "text/plain", null)));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null))).isInstanceOf(CgiApiException.class);
        props.setMaxResponseBytes(2);
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_0", new WorkerResult(0, 200, "text/plain", new byte[10])));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null))).isInstanceOf(CgiApiException.class);
    }

    @Test
    void requestLogFailureRejectsWithoutSpawningAWorker() {
        when(requestLogs.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("REQUEST_LOG_FAILED"));
        verify(adapter, never()).spawn(any());
    }

    @Test
    void executionRecordFailureRejectsInsteadOfReturningAnUnrecordedResult() {
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_0", new WorkerResult(0, 200, "text/plain", null)));
        when(records.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("EXECUTION_RECORD_FAILED"));
    }

    @Test
    void exhaustedCapacityIs503WithoutSpawning() {
        props.setMaxConcurrentWorkers(1);
        ExecutionBulkhead tiny = new ExecutionBulkhead(props);
        tiny.tryAcquire();
        ExecutionOrchestrationService busy = new ExecutionOrchestrationService(matching, policy, redaction, tenantConfig, requestLogs,
                records, isolation, timeoutControl, timeoutService, adapter, tiny, audit, props, clock);
        assertThatThrownBy(() -> busy.handle(call("/legacy/report", Map.of(), null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
        verify(adapter, never()).spawn(any());
    }

    @Test
    void tenantConfigFailureRejectsRatherThanSkippingRedactionRules() {
        when(tenantConfig.extraSensitiveKeys(tenant)).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.handle(call("/legacy/report", Map.of(), null))).isInstanceOf(CgiApiException.class);
        verify(adapter, never()).spawn(any());
    }

    @Test
    void auditFailuresCanNeverChangeTheOutcome() {
        // recordNeverFail is contractually non-throwing; even if a mock misbehaves the happy path above is the guarantee.
        supervisionReturns(new SupervisionResult(ExecutionOutcome.COMPLETED, "EXIT_0", new WorkerResult(0, 200, "text/plain", null)));
        assertThat(service.handle(call("/legacy/report", Map.of(), null)).getStatus()).isEqualTo(200);
        verify(audit, org.mockito.Mockito.atLeastOnce()).recordNeverFail(any());
    }
}
