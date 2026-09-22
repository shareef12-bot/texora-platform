package com.texora.secops.sso.authn.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies an MFA factor code during login.
 *
 * <p><strong>INTERIM STUB — not a real MFA provider.</strong> There is no
 * MFA verification class in texora-iam-client (confirmed: that module only
 * has config/interceptor/mtls/token packages). A real MFA integration
 * (TOTP library, push-notification provider, or similar) was never wired
 * up. Per the CRITICAL CONSTRAINT (no custom crypto/verification logic),
 * this class must NOT grow real TOTP/HOTP algorithm code — when a real
 * provider is chosen, this class should be replaced with a thin adapter to
 * that vetted library, same pattern as every other external-system adapter
 * in this platform, not extended with hand-rolled logic.
 *
 * <p>Current behavior: accepts exactly one fixed test code ("000000") for
 * any enrolled factor, so the login flow (including its integration test)
 * is runnable end-to-end. This is NOT secure and MUST NOT reach production
 * — tracked as a follow-up, same as AuthorizationServerConfig's ephemeral
 * signing key.
 */
@Component
public class MfaVerificationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MfaVerificationClient.class);
    private static final String STUB_VALID_CODE = "000000";

    public boolean verify(UUID dcUserId, String factorType, String submittedCode) {
        LOGGER.warn("Using STUB MFA verification (factorType={}) — accepts only a fixed test code. "
                + "This MUST be replaced with a real MFA provider before production.", factorType);
        return STUB_VALID_CODE.equals(submittedCode);
    }
}