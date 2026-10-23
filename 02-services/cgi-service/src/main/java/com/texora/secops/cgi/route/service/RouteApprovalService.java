package com.texora.secops.cgi.route.service;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.repository.RouteApprovalRepository;
import com.texora.secops.cgi.route.RoutePatterns;
import com.texora.secops.cgi.route.domain.DecisionType;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteApproval;
import com.texora.secops.cgi.route.domain.RouteStatus;
import com.texora.secops.cgi.route.dto.RouteDecisionRequest;
import com.texora.secops.cgi.route.dto.RouteResponse;
import com.texora.secops.cgi.route.dto.RouteSubmitRequest;
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
 * Route allowlist lifecycle with TWO-PERSON approval (CGI-F-001/002). The rule "the submitter can never approve
 * their own change" is enforced HERE, in the service layer - not by documentation or by the UI.
 *
 * <p>Added behaviour (not in the LLD, flagged in the README): a route that is already APPROVED can be revoked by
 * a single {@code REJECTED} decision, because making the gateway stricter must never need two people.
 */
@Service
public class RouteApprovalService {

    private static final Pattern HANDLER_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");
    private static final List<RouteStatus> LIVE_STATUSES = List.of(RouteStatus.DRAFT, RouteStatus.PENDING_APPROVAL, RouteStatus.APPROVED);

    private final RouteAllowlistRepository routes;
    private final RouteApprovalRepository approvals;
    private final WorkerIsolationAdapter adapter;
    private final ExecutionProperties properties;
    private final CgiAuditWriter auditWriter;
    private final Clock clock;

    public RouteApprovalService(RouteAllowlistRepository routes, RouteApprovalRepository approvals, WorkerIsolationAdapter adapter,
                                ExecutionProperties properties, CgiAuditWriter auditWriter, Clock clock) {
        this.routes = routes;
        this.approvals = approvals;
        this.adapter = adapter;
        this.properties = properties;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RouteResponse> list(UUID tenantId, int page, int size) {
        Pageable pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(routes.findByTenantId(tenantId, pageable), RouteApprovalService::toResponse);
    }

    @Transactional
    public RouteResponse submit(CallerContext caller, RouteSubmitRequest request) {
        UUID tenantId = caller.getTenantId();
        String pattern = request.getRoutePattern() == null ? null : request.getRoutePattern().trim();
        String handlerId = request.getApprovedHandlerId() == null ? "" : request.getApprovedHandlerId().trim();
        if (!RoutePatterns.isValid(pattern)) {
            throw CgiApiException.unprocessable("INVALID_ROUTE_PATTERN",
                    "Route must be an exact path of letters, digits and . _ ~ - segments (no wildcards, no traversal)");
        }
        if (!HANDLER_ID.matcher(handlerId).matches() || !adapter.supportsHandler(handlerId)) {
            throw CgiApiException.unprocessable("UNKNOWN_HANDLER", "Handler is not known to the execution adapter");
        }
        int seconds = request.getMaxExecutionSeconds();
        if (seconds < 1 || seconds > properties.getMaxTimeoutCeilingSeconds()) {
            throw CgiApiException.unprocessable("INVALID_TIMEOUT", "Limit must be between 1 and " + properties.getMaxTimeoutCeilingSeconds() + " seconds");
        }
        if (routes.existsByTenantIdAndRoutePatternAndStatusIn(tenantId, pattern, LIVE_STATUSES)) {
            throw CgiApiException.conflict("Route pattern already exists in an active entry");
        }
        Instant now = clock.instant();
        RouteAllowlistEntry entry = new RouteAllowlistEntry(UUID.randomUUID(), tenantId, pattern, handlerId, seconds,
                RouteStatus.PENDING_APPROVAL, caller.getSubject(), now, now);
        try {
            routes.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw CgiApiException.conflict("Route pattern already exists in an active entry");
        }
        auditWriter.recordInTransaction(AuditRecord.of(tenantId, caller.getSubject(), "ROUTE_SUBMITTED", "ROUTE",
                entry.getId().toString(), CgiAuditWriter.OUTCOME_SUCCESS, payload(entry, null)));
        return toResponse(entry);
    }

    @Transactional
    public RouteResponse decide(CallerContext caller, UUID id, RouteDecisionRequest request) {
        UUID tenantId = caller.getTenantId();
        RouteAllowlistEntry entry = routes.findWithLockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> routes.existsById(id) ? CgiApiException.crossTenant() : CgiApiException.notFound("Route allowlist entry"));
        DecisionType decision = request.getDecision();

        if (decision == DecisionType.APPROVED) {
            // Two-person rule first: the submitter gets 403 regardless of the entry's state.
            if (entry.getSubmittedBy() == null || caller.getSubject().equals(entry.getSubmittedBy())) {
                // own transaction: this one is about to roll back
                auditWriter.recordNeverFail(AuditRecord.of(tenantId, caller.getSubject(), "ROUTE_SELF_APPROVAL_BLOCKED", "ROUTE",
                        entry.getId().toString(), CgiAuditWriter.OUTCOME_DENIED, payload(entry, decision)));
                throw CgiApiException.selfApproval();
            }
            if (entry.getStatus() != RouteStatus.PENDING_APPROVAL) {
                throw CgiApiException.conflict("Entry is not pending approval");
            }
            if (!adapter.supportsHandler(entry.getApprovedHandlerId())) {
                throw CgiApiException.unprocessable("UNKNOWN_HANDLER", "Handler is no longer known to the execution adapter");
            }
            entry.setStatus(RouteStatus.APPROVED);
        } else if (decision == DecisionType.REJECTED) {
            if (entry.getStatus() != RouteStatus.PENDING_APPROVAL && entry.getStatus() != RouteStatus.APPROVED) {
                throw CgiApiException.conflict("Entry cannot be rejected in its current state");
            }
            entry.setStatus(RouteStatus.REJECTED);   // from APPROVED this is a revocation (single person is enough)
        } else {
            throw CgiApiException.unprocessable("INVALID_DECISION", "Decision must be APPROVED or REJECTED");
        }

        Instant now = clock.instant();
        entry.setUpdatedAt(now);
        routes.save(entry);
        approvals.save(new RouteApproval(UUID.randomUUID(), tenantId, entry.getId(), caller.getSubject(), decision, now));
        auditWriter.recordInTransaction(AuditRecord.of(tenantId, caller.getSubject(),
                decision == DecisionType.APPROVED ? "ROUTE_APPROVED" : "ROUTE_REJECTED", "ROUTE",
                entry.getId().toString(), CgiAuditWriter.OUTCOME_SUCCESS, payload(entry, decision)));
        return toResponse(entry);
    }

    private static Map<String, Object> payload(RouteAllowlistEntry e, DecisionType decision) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("routePattern", e.getRoutePattern());
        p.put("handlerId", e.getApprovedHandlerId());
        p.put("submittedBy", String.valueOf(e.getSubmittedBy()));
        p.put("status", e.getStatus().name());
        if (decision != null) {
            p.put("decision", decision.name());
        }
        return p;
    }

    static RouteResponse toResponse(RouteAllowlistEntry e) {
        return new RouteResponse(e.getId(), e.getStatus(), e.getRoutePattern(), e.getApprovedHandlerId(),
                e.getMaxExecutionSeconds(), e.getSubmittedBy(), e.getCreatedAt());
    }
}
