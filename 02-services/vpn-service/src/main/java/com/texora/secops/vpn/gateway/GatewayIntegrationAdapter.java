package com.texora.secops.vpn.gateway;

/**
 * THE ONLY seam to the VPN dataplane (01-SHARED-STANDARDS B.4; ADR-D5).
 *
 * <p>Texora does not build the tunnel. The dataplane is an approved vendor product that has NOT been chosen yet, so
 * today the only implementation is {@link LocalStubGatewayIntegrationAdapter}, which is NOT real. No other class in
 * this service may import a dataplane client library or talk to the dataplane. A real implementation must:
 * <ul>
 *   <li>talk to the dataplane over mTLS only,</li>
 *   <li>fetch any credential/certificate material from SEC/KMS at point of use - never persist it, never log it,
 *       never put it in a request/response object of this interface,</li>
 *   <li>treat {@code establish} as idempotent on {@code sessionId} and {@code revoke} as idempotent on
 *       ({@code sessionId}, {@code dataplaneSessionRef}),</li>
 *   <li>throw {@link GatewayUnavailableException} for any connectivity problem or ambiguous outcome - never return
 *       "success" it cannot prove.</li>
 * </ul>
 */
public interface GatewayIntegrationAdapter {

    /** Establish the tunnel. Throws {@link GatewayUnavailableException} if unreachable or the outcome is unknown. */
    GatewayEstablishResult establish(GatewayEstablishRequest request);

    /**
     * Tear down the tunnel. {@code confirmed=true} only when the dataplane positively confirms the session is gone
     * (or never existed). Throws {@link GatewayUnavailableException} when it cannot be reached.
     */
    GatewayRevokeResult revoke(GatewayRevokeRequest request);

    /** Cheap connectivity probe used for the dataplane-connectivity metric. Never throws. */
    boolean isReachable();

    /** Human-readable adapter name, shown on /api/v1/gateway-integration. */
    String adapterName();

    /** True for any non-production implementation. Startup is refused under the 'prod' profile if this is true. */
    boolean isStub();
}
