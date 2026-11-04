package com.texora.secops.vpn.session;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.VpnSession;
import com.texora.secops.vpn.metrics.VpnMetrics;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRevalidationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private final SessionControlService control = mock(SessionControlService.class);
    private final VpnSessionRepository sessions = mock(VpnSessionRepository.class);
    private final VpnProperties props = new VpnProperties();
    private final VpnMetrics metrics = new VpnMetrics(new SimpleMeterRegistry());
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ExecutorService> noExecutor = mock(ObjectProvider.class);
    private final SessionRevalidationScheduler scheduler = new SessionRevalidationScheduler(control, sessions, props, metrics,
            noExecutor, Clock.fixed(NOW, ZoneOffset.UTC));

    private VpnSession session() {
        VpnSession s = new VpnSession();
        s.setId(UUID.randomUUID());
        s.setTenantId(UUID.randomUUID());
        s.setLastValidatedAt(NOW.minusSeconds(1000));
        return s;
    }

    @Test
    void onlyClaimedSessionsAreRevalidated() {
        VpnSession won = session();
        VpnSession lost = session();
        when(sessions.findDueForRevalidationAcrossTenants(any(), any(Pageable.class))).thenReturn(List.of(won, lost));
        when(sessions.claimForRevalidation(eq(won.getId()), any(), any(), any())).thenReturn(1);
        when(sessions.claimForRevalidation(eq(lost.getId()), any(), any(), any())).thenReturn(0);
        assertThat(scheduler.revalidateDue()).isEqualTo(1);
        verify(control).revalidate(won.getTenantId(), won.getId());
        verify(control, never()).revalidate(lost.getTenantId(), lost.getId());
    }

    @Test
    void oneFailingSessionDoesNotStopTheOthers() {
        VpnSession a = session();
        VpnSession b = session();
        when(sessions.findDueForRevalidationAcrossTenants(any(), any(Pageable.class))).thenReturn(List.of(a, b));
        when(sessions.claimForRevalidation(any(), any(), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("x")).when(control).revalidate(a.getTenantId(), a.getId());
        assertThat(scheduler.revalidateDue()).isEqualTo(2);
        verify(control).revalidate(b.getTenantId(), b.getId());
    }

    @Test
    void watchdogRevokesExpiredAndStaleSessionsAndCountsStale() {
        VpnSession expired = session();
        VpnSession stale = session();
        when(sessions.findExpiredAcrossTenants(eq(NOW), any(Pageable.class))).thenReturn(List.of(expired));
        Instant cutoff = NOW.minusSeconds(props.getSession().getMaxUnverifiedSeconds());
        when(sessions.findStaleAcrossTenants(eq(cutoff), any(Pageable.class))).thenReturn(List.of(stale));
        assertThat(scheduler.watchdog()).isEqualTo(2);

        ArgumentCaptor<RevocationCommand> cmd = ArgumentCaptor.forClass(RevocationCommand.class);
        verify(control, org.mockito.Mockito.times(2)).safeRevoke(cmd.capture());
        assertThat(cmd.getAllValues()).extracting(RevocationCommand::getTrigger)
                .containsExactly(VpnConstants.TRIGGER_EXPIRY, VpnConstants.TRIGGER_STALE_VALIDATION);
        assertThat(cmd.getAllValues().get(0).getTerminalStatus()).isEqualTo(VpnConstants.SESSION_EXPIRED);
        assertThat(cmd.getAllValues().get(1).getTerminalStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(cmd.getAllValues()).allMatch(RevocationCommand::isCallDataplane);
        assertThat(metrics.registry().get("vpn.session.stale.detected").counter().count()).isEqualTo(1.0);
    }

    @Test
    void tickSwallowsFailuresSoTheLoopKeepsRunning() {
        when(sessions.findDueForRevalidationAcrossTenants(any(), any(Pageable.class))).thenThrow(new IllegalStateException("db"));
        scheduler.tick();
        verify(control).retryPendingTeardowns();
        when(sessions.findExpiredAcrossTenants(any(), any(Pageable.class))).thenThrow(new IllegalStateException("db"));
        scheduler.watchdogTick();
    }
}
