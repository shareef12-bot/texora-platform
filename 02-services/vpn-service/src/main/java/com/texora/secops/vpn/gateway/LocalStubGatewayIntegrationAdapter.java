package com.texora.secops.vpn.gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * *** NOT REAL. LOCAL STUB. NO VPN TUNNEL EXISTS. ***
 *
 * <p>Stands in for the dataplane vendor product that has not been selected (ADR-D5). It keeps "sessions" in memory,
 * encrypts nothing, routes nothing, and talks to no network. It exists so the control plane can be built and tested
 * (including fault injection: {@link #setSimulatedOutage(boolean)}) against the real adapter INTERFACE.
 *
 * <p>Active only when {@code vpn.gateway.adapter=stub}. The 'prod' profile sets it to {@code vendor}, so with no real
 * adapter bean the application fails to start, and {@code StartupGuard} also refuses to start if this class is wired
 * under 'prod'. Every call logs a WARN so it cannot be missed in a console or a log search.
 */
@Component
@ConditionalOnProperty(name = "vpn.gateway.adapter", havingValue = "stub")
public class LocalStubGatewayIntegrationAdapter implements GatewayIntegrationAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStubGatewayIntegrationAdapter.class);

    private final Map<String, UUID> sessionsByRef = new ConcurrentHashMap<>();
    private final Map<UUID, String> refsBySession = new ConcurrentHashMap<>();
    private final AtomicBoolean simulatedOutage = new AtomicBoolean(false);

    @PostConstruct
    void announce() {
        LOGGER.warn("##########################################################################################");
        LOGGER.warn("# LOCAL STUB VPN GATEWAY ADAPTER IS ACTIVE. THIS IS NOT A REAL VPN. NO TUNNEL IS ESTABLISHED.#");
        LOGGER.warn("# Do not deploy. The dataplane vendor is not chosen yet (ADR-D5).                            #");
        LOGGER.warn("##########################################################################################");
    }

    @Override
    public GatewayEstablishResult establish(GatewayEstablishRequest request) {
        LOGGER.warn("STUB GATEWAY: establish() called for session {} - NOT a real tunnel", request.getSessionId());
        failIfOutage();
        String existing = refsBySession.get(request.getSessionId());
        if (existing != null) {
            return new GatewayEstablishResult(existing); // idempotent on sessionId
        }
        String ref = "stub-" + UUID.randomUUID();
        sessionsByRef.put(ref, request.getSessionId());
        refsBySession.put(request.getSessionId(), ref);
        return new GatewayEstablishResult(ref);
    }

    @Override
    public GatewayRevokeResult revoke(GatewayRevokeRequest request) {
        LOGGER.warn("STUB GATEWAY: revoke() called for session {} (trigger {}) - NOT a real tunnel",
                request.getSessionId(), request.getTriggerCode());
        failIfOutage();
        String ref = request.getDataplaneSessionRef() != null ? request.getDataplaneSessionRef()
                : refsBySession.get(request.getSessionId());
        if (ref != null) {
            UUID removed = sessionsByRef.remove(ref);
            if (removed != null) {
                refsBySession.remove(removed);
            }
        }
        refsBySession.remove(request.getSessionId());
        return new GatewayRevokeResult(true);
    }

    @Override
    public boolean isReachable() {
        return !simulatedOutage.get();
    }

    @Override
    public String adapterName() {
        return "LOCAL-STUB (NOT a real VPN)";
    }

    @Override
    public boolean isStub() {
        return true;
    }

    // ---- test / fault-injection helpers (not part of the adapter interface) ----

    public void setSimulatedOutage(boolean outage) {
        simulatedOutage.set(outage);
    }

    public boolean hasSession(UUID sessionId) {
        return refsBySession.containsKey(sessionId);
    }

    public int sessionCount() {
        return sessionsByRef.size();
    }

    private void failIfOutage() {
        if (simulatedOutage.get()) {
            throw new GatewayUnavailableException("stub: simulated dataplane outage");
        }
    }
}
