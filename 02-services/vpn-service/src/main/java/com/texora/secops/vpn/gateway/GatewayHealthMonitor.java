package com.texora.secops.vpn.gateway;

import com.texora.secops.vpn.metrics.VpnMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Feeds the dataplane-connectivity gauge (first-class metric per LLD s10). Does not gate readiness. */
@Component
public class GatewayHealthMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayHealthMonitor.class);

    private final GatewayIntegrationAdapter adapter;
    private final VpnMetrics metrics;

    public GatewayHealthMonitor(GatewayIntegrationAdapter adapter, VpnMetrics metrics) {
        this.adapter = adapter;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "#{${vpn.dataplane.health-check-seconds:15} * 1000}")
    public void probe() {
        boolean reachable;
        try {
            reachable = adapter.isReachable();
        } catch (RuntimeException e) {
            LOGGER.warn("Dataplane probe threw {}; treating as unreachable", e.getClass().getSimpleName());
            reachable = false;
        }
        metrics.setDataplaneReachable(reachable);
    }
}
