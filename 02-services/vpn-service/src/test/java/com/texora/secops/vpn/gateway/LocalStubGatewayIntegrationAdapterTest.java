package com.texora.secops.vpn.gateway;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStubGatewayIntegrationAdapterTest {

    private final LocalStubGatewayIntegrationAdapter adapter = new LocalStubGatewayIntegrationAdapter();

    private GatewayEstablishRequest req(UUID id) {
        return new GatewayEstablishRequest(id, UUID.randomUUID(), UUID.randomUUID(), "https://gw.example", Instant.now());
    }

    @Test
    void isLoudlyAStub() {
        assertThat(adapter.isStub()).isTrue();
        assertThat(adapter.adapterName()).contains("NOT a real VPN");
    }

    @Test
    void establishIsIdempotentOnSessionId() {
        UUID id = UUID.randomUUID();
        String first = adapter.establish(req(id)).getDataplaneSessionRef();
        assertThat(adapter.establish(req(id)).getDataplaneSessionRef()).isEqualTo(first);
        assertThat(adapter.sessionCount()).isEqualTo(1);
    }

    @Test
    void revokeByRefAndByCorrelationIdBothWorkAndAreIdempotent() {
        UUID a = UUID.randomUUID();
        String ref = adapter.establish(req(a)).getDataplaneSessionRef();
        assertThat(adapter.revoke(new GatewayRevokeRequest(a, ref, "ADMIN")).isConfirmed()).isTrue();
        assertThat(adapter.hasSession(a)).isFalse();
        assertThat(adapter.revoke(new GatewayRevokeRequest(a, ref, "ADMIN")).isConfirmed()).isTrue();

        UUID b = UUID.randomUUID();
        adapter.establish(req(b));
        assertThat(adapter.revoke(new GatewayRevokeRequest(b, null, "ESTABLISH_FAILURE")).isConfirmed()).isTrue();
        assertThat(adapter.hasSession(b)).isFalse();
    }

    @Test
    void simulatedOutageThrowsNeverReturnsSuccess() {
        adapter.setSimulatedOutage(true);
        assertThat(adapter.isReachable()).isFalse();
        assertThatThrownBy(() -> adapter.establish(req(UUID.randomUUID()))).isInstanceOf(GatewayUnavailableException.class);
        assertThatThrownBy(() -> adapter.revoke(new GatewayRevokeRequest(UUID.randomUUID(), "x", "ADMIN")))
                .isInstanceOf(GatewayUnavailableException.class);
        adapter.setSimulatedOutage(false);
        assertThat(adapter.isReachable()).isTrue();
    }
}
