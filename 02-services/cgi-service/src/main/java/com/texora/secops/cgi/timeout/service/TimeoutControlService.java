package com.texora.secops.cgi.timeout.service;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.repository.ExecutionTimeoutControlRepository;
import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.timeout.domain.ExecutionTimeoutControl;
import com.texora.secops.cgi.timeout.dto.TimeoutControlRequest;
import com.texora.secops.cgi.timeout.dto.TimeoutControlResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-route execution time limits (CGI-F-009). Every limit is capped by the platform ceiling. */
@Service
public class TimeoutControlService {

    private final ExecutionTimeoutControlRepository controls;
    private final RouteAllowlistRepository routes;
    private final ExecutionProperties properties;
    private final CgiAuditWriter auditWriter;
    private final Clock clock;

    public TimeoutControlService(ExecutionTimeoutControlRepository controls, RouteAllowlistRepository routes,
                                 ExecutionProperties properties, CgiAuditWriter auditWriter, Clock clock) {
        this.controls = controls;
        this.routes = routes;
        this.properties = properties;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /** Override if present, else the limit approved with the route; always capped by the ceiling. */
    @Transactional(readOnly = true)
    public Duration effectiveTimeout(UUID tenantId, RouteAllowlistEntry route) {
        int seconds = controls.findByTenantIdAndRouteAllowlistEntryId(tenantId, route.getId())
                .map(ExecutionTimeoutControl::getMaxExecutionSeconds)
                .orElse(route.getMaxExecutionSeconds());
        int capped = Math.min(Math.max(seconds, 1), properties.getMaxTimeoutCeilingSeconds());
        return Duration.ofSeconds(capped);
    }

    @Transactional(readOnly = true)
    public PageResponse<TimeoutControlResponse> list(UUID tenantId, int page, int size) {
        Pageable pageable = PageResponse.pageable(page, size, Sort.by("updatedAt").descending());
        return PageResponse.of(controls.findByTenantId(tenantId, pageable), TimeoutControlService::toResponse);
    }

    @Transactional
    public TimeoutControlResponse upsert(CallerContext caller, TimeoutControlRequest request) {
        UUID tenantId = caller.getTenantId();
        int seconds = request.getMaxExecutionSeconds();
        if (seconds < 1 || seconds > properties.getMaxTimeoutCeilingSeconds()) {
            throw CgiApiException.unprocessable("INVALID_TIMEOUT", "Limit must be between 1 and " + properties.getMaxTimeoutCeilingSeconds() + " seconds");
        }
        RouteAllowlistEntry route = routes.findByIdAndTenantId(request.getRouteAllowlistEntryId(), tenantId)
                .orElseThrow(() -> routes.existsById(request.getRouteAllowlistEntryId())
                        ? CgiApiException.crossTenant() : CgiApiException.notFound("Route allowlist entry"));
        if (route.getStatus() != RouteStatus.APPROVED) {
            throw CgiApiException.unprocessable("ROUTE_NOT_APPROVED", "Timeouts can only be set on approved routes");
        }
        Instant now = clock.instant();
        ExecutionTimeoutControl control = controls.findByTenantIdAndRouteAllowlistEntryId(tenantId, route.getId())
                .orElseGet(() -> new ExecutionTimeoutControl(UUID.randomUUID(), tenantId, route.getId(), seconds, caller.getSubject(), now));
        int previous = control.getMaxExecutionSeconds();
        control.setMaxExecutionSeconds(seconds);
        control.setUpdatedBy(caller.getSubject());
        control.setUpdatedAt(now);
        controls.save(control);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routePattern", route.getRoutePattern());
        payload.put("previousSeconds", previous);
        payload.put("newSeconds", seconds);
        auditWriter.recordInTransaction(AuditRecord.of(tenantId, caller.getSubject(), "TIMEOUT_CONTROL_UPDATED", "ROUTE",
                route.getId().toString(), CgiAuditWriter.OUTCOME_SUCCESS, payload));
        return toResponse(control);
    }

    static TimeoutControlResponse toResponse(ExecutionTimeoutControl c) {
        return new TimeoutControlResponse(c.getRouteAllowlistEntryId(), c.getMaxExecutionSeconds(), c.getUpdatedBy(), c.getUpdatedAt());
    }
}
