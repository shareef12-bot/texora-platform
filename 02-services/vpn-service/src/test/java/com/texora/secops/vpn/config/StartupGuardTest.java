package com.texora.secops.vpn.config;

import com.texora.secops.vpn.gateway.GatewayIntegrationAdapter;
import com.texora.secops.vpn.gateway.LocalStubGatewayIntegrationAdapter;
import com.texora.secops.vpn.subject.LocalStubMfaFactorVerifier;
import com.texora.secops.vpn.subject.MfaFactorVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StartupGuardTest {

    private MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }

    @Test
    void stubsAreAllowedOutsideProd() {
        assertThatCode(() -> new StartupGuard(env("dev"), new LocalStubGatewayIntegrationAdapter(),
                new LocalStubMfaFactorVerifier(), new VpnProperties()).check()).doesNotThrowAnyException();
    }

    @Test
    void prodRefusesStubGateway() {
        assertThatThrownBy(() -> new StartupGuard(env("prod"), new LocalStubGatewayIntegrationAdapter(),
                new LocalStubMfaFactorVerifier(), new VpnProperties()).check())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("STUB GatewayIntegrationAdapter");
    }

    @Test
    void prodRefusesStubMfa() {
        GatewayIntegrationAdapter real = mock(GatewayIntegrationAdapter.class);
        when(real.isStub()).thenReturn(false);
        assertThatThrownBy(() -> new StartupGuard(env("prod"), real, new LocalStubMfaFactorVerifier(), new VpnProperties()).check())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA");
    }

    @Test
    void prodRequiresMtlsToSecAndDc() {
        GatewayIntegrationAdapter real = mock(GatewayIntegrationAdapter.class);
        MfaFactorVerifier mfa = mock(MfaFactorVerifier.class);
        assertThatThrownBy(() -> new StartupGuard(env("prod"), real, mfa, new VpnProperties()).check())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("mTLS");
        VpnProperties p = new VpnProperties();
        p.getSec().setSslBundle("sec-client");
        p.getDc().setSslBundle("dc-client");
        assertThatCode(() -> new StartupGuard(env("prod"), real, mfa, p).check()).doesNotThrowAnyException();
    }
}
