package com.texora.secops.vpn.metrics;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.repository.AuditOutboxRepository;
import com.texora.secops.vpn.repository.GatewayBindingRepository;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Gauges for the two failure modes of proactive revocation:
 *  - vpn.session.oldest.unvalidated.seconds  rising toward max-unverified  => UNDER-triggering risk
 *  - vpn.session.revocations (counter, rate)                               => OVER-triggering risk
 * plus teardown backlog (sessions REVOKED here but tunnel not confirmed gone) and audit outbox backlog.
 */
@Component
public class OperationalGauges {

    private final MeterRegistry registry;
    private final VpnSessionRepository sessions;
    private final GatewayBindingRepository bindings;
    private final AuditOutboxRepository outbox;
    private final Clock clock;

    public OperationalGauges(MeterRegistry registry, VpnSessionRepository sessions, GatewayBindingRepository bindings,
                             AuditOutboxRepository outbox, Clock clock) {
        this.registry = registry;
        this.sessions = sessions;
        this.bindings = bindings;
        this.outbox = outbox;
        this.clock = clock;
    }

    @PostConstruct
    void register() {
        Gauge.builder("vpn.session.oldest.unvalidated.seconds", this, OperationalGauges::oldestUnvalidatedSeconds).register(registry);
        Gauge.builder("vpn.sessions.active", this, g -> safe(() -> (double) g.sessions.countByStatus(VpnConstants.SESSION_ACTIVE))).register(registry);
        Gauge.builder("vpn.dataplane.teardown.pending", this, g -> safe(() -> (double) g.bindings.countByTeardownState(VpnConstants.TEARDOWN_PENDING))).register(registry);
        Gauge.builder("vpn.audit.outbox.pending", this, g -> safe(() -> (double) g.outbox.countByPublishedAtIsNull())).register(registry);
    }

    double oldestUnvalidatedSeconds() {
        return safe(() -> {
            Optional<Instant> oldest = sessions.oldestValidationAcrossTenants();
            return oldest.map(i -> (double) Duration.between(i, clock.instant()).getSeconds()).orElse(0.0);
        });
    }

    private static double safe(java.util.function.Supplier<Double> s) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            return Double.NaN; // a gauge that cannot be read must show as missing, not as healthy
        }
    }
}
