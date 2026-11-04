package com.texora.secops.vpn.config;

import com.texora.secops.vpn.gateway.GatewayIntegrationAdapter;
import com.texora.secops.vpn.subject.MfaFactorVerifier;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Production must never run on the local stubs. If the "prod" profile is active and a stub is wired,
 * the application refuses to start: an obviously-failed deploy is safer than a fake VPN gateway or a fixed MFA code.
 */
@Component
public class StartupGuard {

    private final Environment environment;
    private final GatewayIntegrationAdapter adapter;
    private final MfaFactorVerifier mfaVerifier;
    private final VpnProperties properties;

    public StartupGuard(Environment environment, GatewayIntegrationAdapter adapter, MfaFactorVerifier mfaVerifier,
                        VpnProperties properties) {
        this.environment = environment;
        this.adapter = adapter;
        this.mfaVerifier = mfaVerifier;
        this.properties = properties;
    }

    @PostConstruct
    public void check() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (adapter.isStub()) {
            throw new IllegalStateException("REFUSING TO START: the local STUB GatewayIntegrationAdapter is active under the "
                    + "'prod' profile. The VPN dataplane vendor must be chosen (ADR-D5) and a real adapter implemented.");
        }
        if (mfaVerifier.isStub()) {
            throw new IllegalStateException("REFUSING TO START: the local STUB MFA verifier is active under the 'prod' profile.");
        }
        if (isBlank(properties.getSec().getSslBundle()) || isBlank(properties.getDc().getSslBundle())) {
            throw new IllegalStateException("REFUSING TO START: service-to-service calls to SEC and DC must use mTLS "
                    + "(vpn.sec.ssl-bundle / vpn.dc.ssl-bundle) under the 'prod' profile.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
