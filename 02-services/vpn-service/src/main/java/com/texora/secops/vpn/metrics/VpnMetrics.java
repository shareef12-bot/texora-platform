package com.texora.secops.vpn.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * First-class operational metrics for the revocation guarantee (LLD s9.4, s10).
 * Alert rules live in deploy/prometheus-rules.yaml.
 */
@Component
public class VpnMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger dataplaneReachable = new AtomicInteger(1);

    public VpnMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("vpn.dataplane.reachable", dataplaneReachable);
    }

    public void revocation(String trigger, boolean uncertainty) {
        Counter.builder("vpn.session.revocations")
                .tag("trigger", trigger).tag("uncertainty", Boolean.toString(uncertainty))
                .register(registry).increment();
    }

    public void establishment(String outcome) {
        Counter.builder("vpn.session.establishments").tag("outcome", outcome).register(registry).increment();
    }

    public void revalidation(String outcome) {
        Counter.builder("vpn.session.revalidations").tag("outcome", outcome).register(registry).increment();
    }

    public void teardown(String outcome) {
        Counter.builder("vpn.dataplane.teardowns").tag("outcome", outcome).register(registry).increment();
    }

    /** Incremented when the watchdog finds a session the normal re-validation loop failed to cover: under-triggering signal. */
    public void staleSessionDetected() {
        Counter.builder("vpn.session.stale.detected").register(registry).increment();
    }

    public void secUnavailable() {
        Counter.builder("vpn.sec.unavailable").register(registry).increment();
    }

    public void setDataplaneReachable(boolean reachable) {
        dataplaneReachable.set(reachable ? 1 : 0);
    }

    public MeterRegistry registry() {
        return registry;
    }
}
