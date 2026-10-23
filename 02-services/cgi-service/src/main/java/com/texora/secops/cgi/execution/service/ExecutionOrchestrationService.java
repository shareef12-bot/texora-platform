package com.texora.secops.cgi.execution.service;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.audit.service.ParameterRedactionService;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.config.service.TenantConfigService;
import com.texora.secops.cgi.execution.adapter.IsolationSpec;
import com.texora.secops.cgi.execution.adapter.WorkerHandle;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.adapter.WorkerResult;
import com.texora.secops.cgi.execution.adapter.WorkerSpawnException;
import com.texora.secops.cgi.execution.adapter.WorkerSpawnRequest;
import com.texora.secops.cgi.execution.domain.ExecutionOutcome;
import com.texora.secops.cgi.execution.domain.ExecutionRecord;
import com.texora.secops.cgi.execution.domain.RequestLog;
import com.texora.secops.cgi.gateway.dto.GatewayCall;
import com.texora.secops.cgi.gateway.dto.GatewayResponse;
import com.texora.secops.cgi.gateway.service.RouteMatchingService;
import com.texora.secops.cgi.repository.ExecutionRecordRepository;
import com.texora.secops.cgi.repository.RequestLogRepository;
import com.texora.secops.cgi.route.RoutePatterns;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.security.PolicyDecision;
import com.texora.secops.cgi.security.PolicyDecisionClient;
import com.texora.secops.cgi.timeout.service.ExecutionTimeoutService;
import com.texora.secops.cgi.timeout.service.SupervisionResult;
import com.texora.secops.cgi.timeout.service.TimeoutControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The gateway request flow (LLD section 7). Deliberately NOT @Transactional: no database transaction is ever held
 * open while a handler runs. Each persistence step commits on its own.
 *
 * <pre>
 *  1. route match (APPROVED only)  -> else 404, no worker
 *  2. body limit, SEC policy (fail closed), concurrency bulkhead
 *  3. redact -> cgi_request_log (redacted only)   [failure => reject, no worker]
 *  4. effective isolation + timeout; adapter.spawn
 *  5. ExecutionTimeoutService supervises and ALWAYS kills
 *  6. cgi_execution_record; audit (local durable first, async Kafka)
 *  7. COMPLETED -> response | TIMED_OUT -> 504 | FAILED -> 500
 * </pre>
 * FAIL CLOSED: any error before or during execution rejects the request and kills the worker.
 */
@Service
public class ExecutionOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionOrchestrationService.class);
    private static final Pattern METHOD = Pattern.compile("^[A-Z]{3,10}$");
    private static final Pattern CONTENT_TYPE = Pattern.compile("^[A-Za-z0-9!#$&^_.+-]{1,64}/[A-Za-z0-9!#$&^_.+-]{1,64}(?:;\\s*charset=[A-Za-z0-9_.-]{1,32})?$");

    private final RouteMatchingService routeMatchingService;
    private final PolicyDecisionClient policyClient;
    private final ParameterRedactionService redaction;
    private final TenantConfigService tenantConfig;
    private final RequestLogRepository requestLogs;
    private final ExecutionRecordRepository executionRecords;
    private final WorkerIsolationConfigService isolationConfigService;
    private final TimeoutControlService timeoutControlService;
    private final ExecutionTimeoutService timeoutService;
    private final WorkerIsolationAdapter adapter;
    private final ExecutionBulkhead bulkhead;
    private final CgiAuditWriter auditWriter;
    private final ExecutionProperties properties;
    private final Clock clock;

    public ExecutionOrchestrationService(RouteMatchingService routeMatchingService, PolicyDecisionClient policyClient,
                                         ParameterRedactionService redaction, TenantConfigService tenantConfig,
                                         RequestLogRepository requestLogs, ExecutionRecordRepository executionRecords,
                                         WorkerIsolationConfigService isolationConfigService, TimeoutControlService timeoutControlService,
                                         ExecutionTimeoutService timeoutService, WorkerIsolationAdapter adapter,
                                         ExecutionBulkhead bulkhead, CgiAuditWriter auditWriter, ExecutionProperties properties,
                                         Clock clock) {
        this.routeMatchingService = routeMatchingService;
        this.policyClient = policyClient;
        this.redaction = redaction;
        this.tenantConfig = tenantConfig;
        this.requestLogs = requestLogs;
        this.executionRecords = executionRecords;
        this.isolationConfigService = isolationConfigService;
        this.timeoutControlService = timeoutControlService;
        this.timeoutService = timeoutService;
        this.adapter = adapter;
        this.bulkhead = bulkhead;
        this.auditWriter = auditWriter;
        this.properties = properties;
        this.clock = clock;
    }

    public GatewayResponse handle(GatewayCall call) {
        CallerContext caller = call.getCaller();
        UUID tenantId = caller.getTenantId();

        // 1. strict allowlist
        Optional<RouteAllowlistEntry> match = routeMatchingService.match(tenantId, call.getRoutePath());
        if (match.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", "ROUTE_NOT_ALLOWLISTED");
            payload.put("path", RoutePatterns.isValid(call.getRoutePath()) ? call.getRoutePath() : "[INVALID_PATH]");
            auditWriter.recordNeverFail(AuditRecord.of(tenantId, caller.getSubject(), "GATEWAY_REQUEST_REJECTED", "ROUTE",
                    "unmatched", CgiAuditWriter.OUTCOME_DENIED, payload));
            throw CgiApiException.routeNotAllowlisted();
        }
        RouteAllowlistEntry route = match.get();

        // 2. limits, policy, capacity
        long bodyLimit = tenantConfig.maxRequestBodyBytes(tenantId);
        if (call.getBody().length > bodyLimit) {
            audit(call, route, "GATEWAY_REQUEST_REJECTED", CgiAuditWriter.OUTCOME_DENIED, Map.of("reason", "BODY_TOO_LARGE"));
            throw CgiApiException.unprocessable("BODY_TOO_LARGE", "Request body exceeds the allowed size");
        }
        PolicyDecision decision = policyClient.evaluate(caller, "cgi.route.invoke", route.getRoutePattern());
        Map<String, Object> policyPayload = new LinkedHashMap<>();
        policyPayload.put("action", "cgi.route.invoke");
        policyPayload.put("reason", decision.getReasonCode());
        audit(call, route, "POLICY_DECISION", decision.isAllowed() ? CgiAuditWriter.OUTCOME_SUCCESS : CgiAuditWriter.OUTCOME_DENIED, policyPayload);
        if (!decision.isAllowed()) {
            throw decision.isTimeout() ? CgiApiException.policyTimeout() : CgiApiException.policyDenied();
        }
        if (!bulkhead.tryAcquire()) {
            audit(call, route, "GATEWAY_REQUEST_REJECTED", CgiAuditWriter.OUTCOME_DENIED, Map.of("reason", "GATEWAY_BUSY"));
            throw CgiApiException.gatewayBusy();
        }
        try {
            return execute(call, route);
        } finally {
            bulkhead.release();
        }
    }

    private GatewayResponse execute(GatewayCall call, RouteAllowlistEntry route) {
        UUID tenantId = call.getCaller().getTenantId();

        // 3. redact FIRST, then persist (redacted only). Any failure -> reject, no worker.
        UUID requestLogId = UUID.randomUUID();
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("method", METHOD.matcher(String.valueOf(call.getMethod())).matches() ? call.getMethod() : "OTHER");
            meta.put("params", redaction.redactParameters(call.getParameters(), tenantConfig.extraSensitiveKeys(tenantId)));
            meta.put("bodyBytes", call.getBody().length);        // body CONTENT is never stored
            meta.put("contentType", sanitiseContentType(call.getContentType()));
            requestLogs.save(new RequestLog(requestLogId, tenantId, route.getId(), call.getCaller().getSubject(), clock.instant(), meta));
        } catch (RuntimeException e) {
            LOGGER.error("Request log could not be written - rejecting request, no worker spawned ({})", e.getClass().getName());
            audit(call, route, "GATEWAY_REQUEST_FAILED", CgiAuditWriter.OUTCOME_FAILURE, Map.of("reason", "REQUEST_LOG_FAILED"));
            throw CgiApiException.internal("REQUEST_LOG_FAILED", "Request could not be recorded; rejected");
        }

        // 4. isolation + limit (service-decided; the request cannot influence either)
        IsolationSpec spec;
        Duration limit;
        try {
            spec = isolationConfigService.effectiveSpec(tenantId, route.getApprovedHandlerId());
            limit = timeoutControlService.effectiveTimeout(tenantId, route);
        } catch (RuntimeException e) {
            audit(call, route, "GATEWAY_REQUEST_FAILED", CgiAuditWriter.OUTCOME_FAILURE, Map.of("reason", "CONFIG_UNAVAILABLE"));
            throw e instanceof CgiApiException ? e : CgiApiException.internal("CONFIG_UNAVAILABLE", "Execution settings unavailable; rejected");
        }

        WorkerSpawnRequest spawn = new WorkerSpawnRequest(tenantId, call.getCaller().getSubject(), requestLogId, spec,
                call.getMethod(), call.getRoutePath(), call.getContentType(), toListMap(call.getParameters()), call.getBody());

        Instant started = clock.instant();
        WorkerHandle handle = null;
        SupervisionResult supervision;
        try {
            handle = adapter.spawn(spawn);
            supervision = timeoutService.supervise(handle, limit);          // 5. timer + forced kill
        } catch (WorkerSpawnException e) {
            supervision = SupervisionResult.failed("SPAWN_FAILED");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error during execution - killing worker and failing closed", e);
            timeoutService.killQuietly(handle);
            supervision = SupervisionResult.failed("UNEXPECTED_ERROR");
        }

        // 6. classify
        ExecutionOutcome outcome = supervision.getOutcome();
        String reason = supervision.getReason();
        GatewayResponse response = null;
        if (outcome == ExecutionOutcome.COMPLETED) {
            String problem = validate(supervision.getResult());
            if (problem != null) {
                outcome = ExecutionOutcome.FAILED;
                reason = problem;
            } else {
                WorkerResult wr = supervision.getResult();
                response = new GatewayResponse(wr.getHttpStatus(), sanitiseContentType(wr.getContentType()), wr.getBody());
            }
        }
        Instant ended = clock.instant();

        try {
            executionRecords.save(new ExecutionRecord(UUID.randomUUID(), tenantId, requestLogId,
                    handle == null ? "none" : clip(handle.workerId(), 128), started, ended, outcome, clip(reason, 255)));
        } catch (RuntimeException e) {
            LOGGER.error("Execution record could not be written - rejecting (uncertain state) ({})", e.getClass().getName());
            audit(call, route, "GATEWAY_EXECUTE", CgiAuditWriter.OUTCOME_FAILURE, executionPayload(route, requestLogId, "FAILED", "EXECUTION_RECORD_FAILED", started, ended));
            throw CgiApiException.internal("EXECUTION_RECORD_FAILED", "Request could not be completed; rejected");
        }

        audit(call, route, "GATEWAY_EXECUTE",
                outcome == ExecutionOutcome.COMPLETED ? CgiAuditWriter.OUTCOME_SUCCESS : CgiAuditWriter.OUTCOME_FAILURE,
                executionPayload(route, requestLogId, outcome.name(), reason, started, ended));

        // 7. answer
        return switch (outcome) {
            case COMPLETED -> response;
            case TIMED_OUT -> throw CgiApiException.executionTimeout();
            case FAILED -> throw CgiApiException.executionFailed();
        };
    }

    private String validate(WorkerResult result) {
        if (result == null) {
            return "NULL_RESULT";
        }
        if (result.getExitCode() != 0) {
            return "EXIT_NONZERO";
        }
        if (result.getHttpStatus() < 100 || result.getHttpStatus() > 599) {
            return "BAD_STATUS";
        }
        if (result.bodyLength() > properties.getMaxResponseBytes()) {
            return "RESPONSE_TOO_LARGE";
        }
        return null;
    }

    private Map<String, Object> executionPayload(RouteAllowlistEntry route, UUID requestLogId, String outcome, String reason,
                                                 Instant started, Instant ended) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("routePattern", route.getRoutePattern());
        p.put("handlerId", route.getApprovedHandlerId());
        p.put("requestLogId", requestLogId.toString());
        p.put("executionOutcome", outcome);
        p.put("reason", reason);
        p.put("durationMs", Duration.between(started, ended).toMillis());
        return p;
    }

    /** Audit never blocks and never throws (see CgiAuditWriter#recordNeverFail). */
    private void audit(GatewayCall call, RouteAllowlistEntry route, String action, String outcome, Map<String, Object> payload) {
        auditWriter.recordNeverFail(AuditRecord.of(call.getCaller().getTenantId(), call.getCaller().getSubject(), action,
                "ROUTE", route.getId().toString(), outcome, payload));
    }

    private static Map<String, List<String>> toListMap(Map<String, String[]> parameters) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : parameters.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? List.of() : Arrays.asList(e.getValue()));
        }
        return out;
    }

    private static String sanitiseContentType(String contentType) {
        return contentType != null && CONTENT_TYPE.matcher(contentType.trim()).matches() ? contentType.trim() : "application/octet-stream";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
