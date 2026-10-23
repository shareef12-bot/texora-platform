package com.texora.secops.cgi.execution.service;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.execution.adapter.IsolationSpec;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.domain.IsolationConfigStatus;
import com.texora.secops.cgi.execution.domain.WorkerIsolationConfig;
import com.texora.secops.cgi.execution.dto.IsolationConfigRequest;
import com.texora.secops.cgi.execution.dto.IsolationConfigResponse;
import com.texora.secops.cgi.execution.dto.IsolationDecisionRequest;
import com.texora.secops.cgi.repository.WorkerIsolationConfigRepository;
import com.texora.secops.cgi.route.domain.DecisionType;
import com.texora.secops.cgi.security.CallerContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Worker isolation settings (CGI-F-008). With no ACTIVE row a handler gets the non-negotiable defaults
 * (sandboxed, non-root, no network). ANY change - including loosening - goes through the same two-person
 * approval as the route allowlist: the submitter can never activate their own change (LLD 9.4).
 */
@Service
public class WorkerIsolationConfigService {

    private static final Pattern PROFILE = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final Pattern HANDLER_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");

    private final WorkerIsolationConfigRepository repository;
    private final WorkerIsolationAdapter adapter;
    private final ExecutionProperties properties;
    private final CgiAuditWriter auditWriter;
    private final Clock clock;

    public WorkerIsolationConfigService(WorkerIsolationConfigRepository repository, WorkerIsolationAdapter adapter,
                                        ExecutionProperties properties, CgiAuditWriter auditWriter, Clock clock) {
        this.repository = repository;
        this.adapter = adapter;
        this.properties = properties;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /** The isolation the worker for this handler MUST get. More than one ACTIVE row is impossible - if seen, fail closed. */
    @Transactional(readOnly = true)
    public IsolationSpec effectiveSpec(UUID tenantId, String handlerId) {
        List<WorkerIsolationConfig> active = repository.findByTenantIdAndHandlerIdAndStatus(tenantId, handlerId, IsolationConfigStatus.ACTIVE);
        if (active.isEmpty()) {
            return IsolationSpec.strictDefaults(handlerId, properties.getDefaultSandboxProfile());
        }
        if (active.size() > 1) {
            throw CgiApiException.internal("ISOLATION_CONFIG_AMBIGUOUS", "Isolation configuration is ambiguous; request denied");
        }
        WorkerIsolationConfig c = active.get(0);
        return new IsolationSpec(handlerId, c.getSandboxProfile(), c.isNetworkAllowed(), c.isRunAsNonRoot());
    }

    @Transactional(readOnly = true)
    public PageResponse<IsolationConfigResponse> list(UUID tenantId, int page, int size) {
        Pageable pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(repository.findByTenantId(tenantId, pageable), WorkerIsolationConfigService::toResponse);
    }

    @Transactional
    public IsolationConfigResponse submit(CallerContext caller, IsolationConfigRequest request) {
        UUID tenantId = caller.getTenantId();
        String handlerId = request.getHandlerId() == null ? "" : request.getHandlerId().trim();
        String profile = request.getSandboxProfile() == null ? "" : request.getSandboxProfile().trim();
        if (!HANDLER_ID.matcher(handlerId).matches() || !adapter.supportsHandler(handlerId)) {
            throw CgiApiException.unprocessable("UNKNOWN_HANDLER", "Handler is not known to the execution adapter");
        }
        if (!PROFILE.matcher(profile).matches()) {
            throw CgiApiException.unprocessable("INVALID_SANDBOX_PROFILE", "Sandbox profile name is invalid");
        }
        if (repository.existsByTenantIdAndHandlerIdAndStatus(tenantId, handlerId, IsolationConfigStatus.PENDING_APPROVAL)) {
            throw CgiApiException.conflict("A change for this handler is already awaiting approval");
        }
        boolean network = Boolean.TRUE.equals(request.getNetworkAllowed());                 // omitted => false
        boolean nonRoot = request.getRunAsNonRoot() == null || request.getRunAsNonRoot();   // omitted => true
        WorkerIsolationConfig config = new WorkerIsolationConfig(UUID.randomUUID(), tenantId, handlerId, profile, network, nonRoot,
                IsolationConfigStatus.PENDING_APPROVAL, caller.getSubject(), null, null, clock.instant());
        try {
            repository.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            throw CgiApiException.conflict("A change for this handler is already awaiting approval");
        }
        audit(caller, "ISOLATION_CONFIG_SUBMITTED", config, CgiAuditWriter.OUTCOME_SUCCESS, "PENDING_APPROVAL");
        return toResponse(config);
    }

    @Transactional
    public IsolationConfigResponse decide(CallerContext caller, UUID id, IsolationDecisionRequest request) {
        WorkerIsolationConfig config = repository.findWithLockByIdAndTenantId(id, caller.getTenantId())
                .orElseThrow(() -> repository.existsById(id) ? CgiApiException.crossTenant() : CgiApiException.notFound("Isolation config"));
        if (config.getStatus() != IsolationConfigStatus.PENDING_APPROVAL) {
            throw CgiApiException.conflict("Change is not pending approval");
        }
        if (request.getDecision() == DecisionType.APPROVED) {
            if (config.getSubmittedBy() == null || caller.getSubject().equals(config.getSubmittedBy())) {
                audit(caller, "ISOLATION_SELF_APPROVAL_BLOCKED", config, CgiAuditWriter.OUTCOME_DENIED, "SELF_APPROVAL");
                throw CgiApiException.selfApproval();
            }
            if (!adapter.supportsHandler(config.getHandlerId())) {
                throw CgiApiException.unprocessable("UNKNOWN_HANDLER", "Handler is not known to the execution adapter");
            }
            for (WorkerIsolationConfig old : repository.findByTenantIdAndHandlerIdAndStatus(
                    caller.getTenantId(), config.getHandlerId(), IsolationConfigStatus.ACTIVE)) {
                old.setStatus(IsolationConfigStatus.SUPERSEDED);
                repository.saveAndFlush(old);   // flush first so the one-ACTIVE unique index is never violated
            }
            config.setStatus(IsolationConfigStatus.ACTIVE);
        } else {
            config.setStatus(IsolationConfigStatus.REJECTED);
        }
        config.setDecidedBy(caller.getSubject());
        config.setDecidedAt(clock.instant());
        repository.save(config);
        audit(caller, request.getDecision() == DecisionType.APPROVED ? "ISOLATION_CONFIG_APPROVED" : "ISOLATION_CONFIG_REJECTED",
                config, CgiAuditWriter.OUTCOME_SUCCESS, config.getStatus().name());
        return toResponse(config);
    }

    private void audit(CallerContext caller, String action, WorkerIsolationConfig c, String outcome, String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handlerId", c.getHandlerId());
        payload.put("sandboxProfile", c.getSandboxProfile());
        payload.put("networkAllowed", c.isNetworkAllowed());
        payload.put("runAsNonRoot", c.isRunAsNonRoot());
        payload.put("detail", detail);
        AuditRecord record = AuditRecord.of(caller.getTenantId(), caller.getSubject(), action, "WORKER_ISOLATION",
                c.getId().toString(), outcome, payload);
        if (CgiAuditWriter.OUTCOME_DENIED.equals(outcome)) {
            auditWriter.recordNeverFail(record);      // the surrounding transaction is about to roll back
        } else {
            auditWriter.recordInTransaction(record);
        }
    }

    static IsolationConfigResponse toResponse(WorkerIsolationConfig c) {
        return new IsolationConfigResponse(c.getId(), c.getHandlerId(), c.getSandboxProfile(), c.isNetworkAllowed(),
                c.isRunAsNonRoot(), c.getStatus(), c.getSubmittedBy(), c.getDecidedBy(), c.getDecidedAt(), c.getCreatedAt());
    }
}
