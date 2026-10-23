package com.texora.secops.vpn.session;

import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.VpnSession;
import com.texora.secops.vpn.metrics.VpnMetrics;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Drives re-validation. Safe with HPA >= 2 replicas: a session is claimed with an atomic UPDATE, and the claim lease
 * expires on its own if a replica dies mid-way.
 *
 * Three independent mechanisms, so no single failure leaves a stale session alive:
 *  1. tick        - claim and re-validate every session whose next_validation_at is due (includes event-driven triggers,
 *                   which simply set next_validation_at = now).
 *  2. watchdog    - revokes sessions past expires_at, and sessions NOT successfully validated within max-unverified
 *                   (catches a stalled/crashed re-validation loop = the under-triggering failure mode).
 *  3. teardown    - retries dataplane teardown until the dataplane confirms.
 */
@Component
public class SessionRevalidationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionRevalidationScheduler.class);

    private final SessionControlService control;
    private final VpnSessionRepository sessions;
    private final VpnProperties properties;
    private final VpnMetrics metrics;
    private final ObjectProvider<ExecutorService> executorProvider;
    private final Clock clock;

    public SessionRevalidationScheduler(SessionControlService control, VpnSessionRepository sessions, VpnProperties properties,
                                        VpnMetrics metrics, ObjectProvider<ExecutorService> executorProvider, Clock clock) {
        this.control = control;
        this.sessions = sessions;
        this.properties = properties;
        this.metrics = metrics;
        this.executorProvider = executorProvider;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${vpn.scheduling.tick-ms:5000}")
    public void tick() {
        try {
            revalidateDue();
        } catch (RuntimeException e) {
            LOGGER.error("Re-validation tick failed", e);
        }
        try {
            control.retryPendingTeardowns();
        } catch (RuntimeException e) {
            LOGGER.error("Teardown retry failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${vpn.scheduling.watchdog-ms:15000}")
    public void watchdogTick() {
        try {
            watchdog();
        } catch (RuntimeException e) {
            LOGGER.error("Watchdog failed", e);
        }
    }

    /** @return number of sessions this replica claimed and re-validated. */
    public int revalidateDue() {
        Instant now = clock.instant();
        Instant lease = now.plusSeconds(properties.getSession().getClaimLeaseSeconds());
        List<VpnSession> due = sessions.findDueForRevalidationAcrossTenants(now,
                PageRequest.of(0, properties.getSession().getBatchSize()));
        ExecutorService executor = executorProvider.getIfAvailable();
        List<Future<?>> futures = new ArrayList<>();
        int claimed = 0;
        for (VpnSession s : due) {
            if (sessions.claimForRevalidation(s.getId(), s.getTenantId(), now, lease) != 1) {
                continue;
            }
            claimed++;
            Runnable task = () -> {
                try {
                    control.revalidate(s.getTenantId(), s.getId());
                } catch (RuntimeException e) {
                    // revalidate() already fails closed internally; this only logs a bookkeeping failure.
                    LOGGER.error("Re-validation task for session {} failed", s.getId(), e);
                }
            };
            if (executor == null) {
                task.run();
            } else {
                futures.add(executor.submit(task));
            }
        }
        long waitMs = properties.getSec().getRevalidationDeadlineMs() + 10_000L;
        for (Future<?> f : futures) {
            try {
                f.get(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warn("Re-validation task did not finish in time: {}", e.getClass().getSimpleName());
            }
        }
        return claimed;
    }

    /** @return number of sessions revoked by the watchdog. */
    public int watchdog() {
        Instant now = clock.instant();
        int revoked = 0;
        for (VpnSession s : sessions.findExpiredAcrossTenants(now, PageRequest.of(0, properties.getSession().getBatchSize()))) {
            control.safeRevoke(expiry(s));
            revoked++;
        }
        Instant cutoff = now.minusSeconds(properties.getSession().getMaxUnverifiedSeconds());
        for (VpnSession s : sessions.findStaleAcrossTenants(cutoff, PageRequest.of(0, properties.getSession().getBatchSize()))) {
            LOGGER.error("Session {} was not validated since {}; REVOKING (stale validation)", s.getId(), s.getLastValidatedAt());
            metrics.staleSessionDetected();
            control.safeRevoke(stale(s));
            revoked++;
        }
        return revoked;
    }

    private RevocationCommand expiry(VpnSession s) {
        return cmd(s, com.texora.secops.vpn.VpnConstants.TRIGGER_EXPIRY, "session lifetime ended",
                com.texora.secops.vpn.VpnConstants.SESSION_EXPIRED);
    }

    private RevocationCommand stale(VpnSession s) {
        return cmd(s, com.texora.secops.vpn.VpnConstants.TRIGGER_STALE_VALIDATION,
                "session was not successfully re-validated within the allowed window",
                com.texora.secops.vpn.VpnConstants.SESSION_REVOKED);
    }

    private RevocationCommand cmd(VpnSession s, String trigger, String reason, String terminal) {
        RevocationCommand c = new RevocationCommand();
        c.setTenantId(s.getTenantId());
        c.setSessionId(s.getId());
        c.setTrigger(trigger);
        c.setReason(reason);
        c.setActorId(com.texora.secops.vpn.VpnConstants.SYSTEM_ACTOR);
        c.setTerminalStatus(terminal);
        c.setCallDataplane(true);
        c.setDataplaneMayExist(true);
        return c;
    }
}
