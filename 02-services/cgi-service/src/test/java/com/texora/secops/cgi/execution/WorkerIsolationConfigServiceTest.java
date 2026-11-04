package com.texora.secops.cgi.execution;

import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.execution.adapter.IsolationSpec;
import com.texora.secops.cgi.execution.adapter.WorkerIsolationAdapter;
import com.texora.secops.cgi.execution.domain.IsolationConfigStatus;
import com.texora.secops.cgi.execution.domain.WorkerIsolationConfig;
import com.texora.secops.cgi.execution.dto.IsolationConfigRequest;
import com.texora.secops.cgi.execution.dto.IsolationConfigResponse;
import com.texora.secops.cgi.execution.dto.IsolationDecisionRequest;
import com.texora.secops.cgi.execution.service.WorkerIsolationConfigService;
import com.texora.secops.cgi.repository.WorkerIsolationConfigRepository;
import com.texora.secops.cgi.route.domain.DecisionType;
import com.texora.secops.cgi.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerIsolationConfigServiceTest {

    private final WorkerIsolationConfigRepository repo = mock(WorkerIsolationConfigRepository.class);
    private final WorkerIsolationAdapter adapter = mock(WorkerIsolationAdapter.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private WorkerIsolationConfigService service;
    private final UUID tenant = UUID.randomUUID();
    private final CallerContext submitter = new CallerContext(tenant, UUID.randomUUID(), Set.of());
    private final CallerContext approver = new CallerContext(tenant, UUID.randomUUID(), Set.of());

    @BeforeEach
    void setUp() {
        when(adapter.supportsHandler("stub-echo")).thenReturn(true);
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service = new WorkerIsolationConfigService(repo, adapter, new ExecutionProperties(), audit, Clock.systemUTC());
    }

    private WorkerIsolationConfig pending(boolean network) {
        return new WorkerIsolationConfig(UUID.randomUUID(), tenant, "stub-echo", "strict-default", network, true,
                IsolationConfigStatus.PENDING_APPROVAL, submitter.getSubject(), null, null, Instant.now());
    }

    @Test
    void noActiveRowMeansNonNegotiableDefaults() {
        when(repo.findByTenantIdAndHandlerIdAndStatus(tenant, "stub-echo", IsolationConfigStatus.ACTIVE)).thenReturn(List.of());
        IsolationSpec spec = service.effectiveSpec(tenant, "stub-echo");
        assertThat(spec.isNetworkAllowed()).isFalse();
        assertThat(spec.isRunAsNonRoot()).isTrue();
    }

    @Test
    void ambiguousActiveRowsFailClosed() {
        when(repo.findByTenantIdAndHandlerIdAndStatus(tenant, "stub-echo", IsolationConfigStatus.ACTIVE))
                .thenReturn(List.of(pending(false), pending(false)));
        assertThatThrownBy(() -> service.effectiveSpec(tenant, "stub-echo")).isInstanceOf(CgiApiException.class);
    }

    @Test
    void omittedBooleansTakeSafeDefaultsAndStartPending() {
        IsolationConfigResponse r = service.submit(submitter, new IsolationConfigRequest("stub-echo", "strict-default", null, null));
        assertThat(r.isNetworkAllowed()).isFalse();
        assertThat(r.isRunAsNonRoot()).isTrue();
        assertThat(r.getStatus()).isEqualTo(IsolationConfigStatus.PENDING_APPROVAL);
    }

    @Test
    void looseningRequestIsPendingNotActive() {
        IsolationConfigResponse r = service.submit(submitter, new IsolationConfigRequest("stub-echo", "strict-default", true, false));
        assertThat(r.getStatus()).isEqualTo(IsolationConfigStatus.PENDING_APPROVAL);
        assertThat(r.isNetworkAllowed()).isTrue();
    }

    @Test
    void unknownHandlerBadProfileAndSecondPendingAreRejected() {
        assertThatThrownBy(() -> service.submit(submitter, new IsolationConfigRequest("evil", "strict-default", null, null)))
                .isInstanceOf(CgiApiException.class);
        assertThatThrownBy(() -> service.submit(submitter, new IsolationConfigRequest("stub-echo", "Bad Profile!", null, null)))
                .isInstanceOf(CgiApiException.class);
        when(repo.existsByTenantIdAndHandlerIdAndStatus(tenant, "stub-echo", IsolationConfigStatus.PENDING_APPROVAL)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(submitter, new IsolationConfigRequest("stub-echo", "strict-default", null, null)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void submitterCannotActivateOwnChange() {
        WorkerIsolationConfig c = pending(true);
        when(repo.findWithLockByIdAndTenantId(c.getId(), tenant)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.decide(submitter, c.getId(), new IsolationDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_APPROVAL_BLOCKED"));
        assertThat(c.getStatus()).isEqualTo(IsolationConfigStatus.PENDING_APPROVAL);
        verify(repo, never()).save(any());
    }

    @Test
    void secondPersonApprovalActivatesAndSupersedesTheOldRow() {
        WorkerIsolationConfig c = pending(false);
        WorkerIsolationConfig old = pending(false);
        old.setStatus(IsolationConfigStatus.ACTIVE);
        when(repo.findWithLockByIdAndTenantId(c.getId(), tenant)).thenReturn(Optional.of(c));
        when(repo.findByTenantIdAndHandlerIdAndStatus(tenant, "stub-echo", IsolationConfigStatus.ACTIVE)).thenReturn(List.of(old));
        IsolationConfigResponse r = service.decide(approver, c.getId(), new IsolationDecisionRequest(DecisionType.APPROVED));
        assertThat(r.getStatus()).isEqualTo(IsolationConfigStatus.ACTIVE);
        assertThat(old.getStatus()).isEqualTo(IsolationConfigStatus.SUPERSEDED);
        assertThat(r.getDecidedBy()).isEqualTo(approver.getSubject());
    }

    @Test
    void rejectionAndStateAndTenantChecks() {
        WorkerIsolationConfig c = pending(false);
        when(repo.findWithLockByIdAndTenantId(c.getId(), tenant)).thenReturn(Optional.of(c));
        assertThat(service.decide(approver, c.getId(), new IsolationDecisionRequest(DecisionType.REJECTED)).getStatus())
                .isEqualTo(IsolationConfigStatus.REJECTED);
        assertThatThrownBy(() -> service.decide(approver, c.getId(), new IsolationDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
        UUID other = UUID.randomUUID();
        when(repo.findWithLockByIdAndTenantId(other, tenant)).thenReturn(Optional.empty());
        when(repo.existsById(other)).thenReturn(true);
        assertThatThrownBy(() -> service.decide(approver, other, new IsolationDecisionRequest(DecisionType.APPROVED)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    @Test
    void listIsTenantScoped() {
        when(repo.findByTenantId(eq(tenant), any())).thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(service.list(tenant, 0, 10).getItems()).isEmpty();
    }
}
