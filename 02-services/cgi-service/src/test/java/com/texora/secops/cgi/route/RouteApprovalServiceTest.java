package com.texora.secops.cgi.route;

import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.repository.RouteApprovalRepository;
import com.texora.secops.cgi.route.domain.DecisionType;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteApproval;
import com.texora.secops.cgi.route.domain.RouteStatus;
import com.texora.secops.cgi.route.dto.RouteDecisionRequest;
import com.texora.secops.cgi.route.dto.RouteResponse;
import com.texora.secops.cgi.route.dto.RouteSubmitRequest;
import com.texora.secops.cgi.route.service.RouteApprovalService;
import com.texora.secops.cgi.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteApprovalServiceTest {

    private final RouteAllowlistRepository routes = mock(RouteAllowlistRepository.class);
    private final RouteApprovalRepository approvals = mock(RouteApprovalRepository.class);
    private final WorkerIsolationAdapter adapter = mock(WorkerIsolationAdapter.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private RouteApprovalService service;

    private final UUID tenant = UUID.randomUUID();
    private final CallerContext submitter = new CallerContext(tenant, UUID.randomUUID(), Set.of());
    private final CallerContext approver = new CallerContext(tenant, UUID.randomUUID(), Set.of());

    @BeforeEach
    void setUp() {
        when(adapter.supportsHandler("stub-echo")).thenReturn(true);
        when(routes.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service = new RouteApprovalService(routes, approvals, adapter, new ExecutionProperties(), audit, clock);
    }

    private RouteSubmitRequest request(String pattern, String handler, int seconds) {
        return new RouteSubmitRequest(pattern, handler, seconds);
    }

    private RouteAllowlistEntry pending() {
        return new RouteAllowlistEntry(UUID.randomUUID(), tenant, "/legacy/report", "stub-echo", 5, RouteStatus.PENDING_APPROVAL,
                submitter.getSubject(), Instant.now(), Instant.now());
    }

    @Test
    void submitCreatesPendingEntryAndAudits() {
        RouteResponse r = service.submit(submitter, request("/legacy/report", "stub-echo", 5));
        assertThat(r.getStatus()).isEqualTo(RouteStatus.PENDING_APPROVAL);
        assertThat(r.getSubmittedBy()).isEqualTo(submitter.getSubject());
        verify(audit).recordInTransaction(any());
    }

    @Test
    void submitRejectsBadPatternUnknownHandlerAndBadTimeout() {
        assertThatThrownBy(() -> service.submit(submitter, request("/a/../b", "stub-echo", 5))).isInstanceOf(CgiApiException.class);
        assertThatThrownBy(() -> service.submit(submitter, request("/ok", "not-a-handler", 5))).isInstanceOf(CgiApiException.class);
        assertThatThrownBy(() -> service.submit(submitter, request("/ok", "stub-echo", 100000))).isInstanceOf(CgiApiException.class);
        verify(routes, never()).saveAndFlush(any());
    }

    @Test
    void duplicateLivePatternIs409() {
        when(routes.existsByTenantIdAndRoutePatternAndStatusIn(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.submit(submitter, request("/legacy/report", "stub-echo", 5)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void submitterCanNeverApproveOwnEntry() {
        RouteAllowlistEntry entry = pending();
        when(routes.findWithLockByIdAndTenantId(entry.getId(), tenant)).thenReturn(Optional.of(entry));
        assertThatThrownBy(() -> service.decide(submitter, entry.getId(), new RouteDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_APPROVAL_BLOCKED"));
        assertThat(entry.getStatus()).isEqualTo(RouteStatus.PENDING_APPROVAL);
        verify(approvals, never()).save(any());
        verify(audit).recordNeverFail(any());
    }

    @Test
    void differentApproverApproves() {
        RouteAllowlistEntry entry = pending();
        when(routes.findWithLockByIdAndTenantId(entry.getId(), tenant)).thenReturn(Optional.of(entry));
        RouteResponse r = service.decide(approver, entry.getId(), new RouteDecisionRequest(DecisionType.APPROVED));
        assertThat(r.getStatus()).isEqualTo(RouteStatus.APPROVED);
        verify(approvals).save(any(RouteApproval.class));
        verify(audit).recordInTransaction(any());
    }

    @Test
    void approvingNonPendingEntryIs409() {
        RouteAllowlistEntry entry = pending();
        entry.setStatus(RouteStatus.REJECTED);
        when(routes.findWithLockByIdAndTenantId(entry.getId(), tenant)).thenReturn(Optional.of(entry));
        assertThatThrownBy(() -> service.decide(approver, entry.getId(), new RouteDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void approvedRouteCanBeRevokedByRejection() {
        RouteAllowlistEntry entry = pending();
        entry.setStatus(RouteStatus.APPROVED);
        when(routes.findWithLockByIdAndTenantId(entry.getId(), tenant)).thenReturn(Optional.of(entry));
        assertThat(service.decide(approver, entry.getId(), new RouteDecisionRequest(DecisionType.REJECTED)).getStatus())
                .isEqualTo(RouteStatus.REJECTED);
    }

    @Test
    void foreignTenantEntryIs403AndMissingEntryIs404() {
        UUID id = UUID.randomUUID();
        when(routes.findWithLockByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        when(routes.existsById(id)).thenReturn(true);
        assertThatThrownBy(() -> service.decide(approver, id, new RouteDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        when(routes.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> service.decide(approver, id, new RouteDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    }

    @Test
    void listIsTenantScoped() {
        when(routes.findByTenantId(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(service.list(tenant, 0, 10).getItems()).isEmpty();
        verify(routes).findByTenantId(any(), any());
    }
}
