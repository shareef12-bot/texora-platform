package com.texora.secops.vpn.subject;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * *** NOT REAL MFA. LOCAL STUB. *** Accepts exactly one fixed test code ("000000") for every factor.
 * Same pattern as sso-service's ephemeral signing key. Active only when vpn.mfa.stub-enabled=true; the 'prod'
 * profile turns that off (no verifier bean -> startup fails) and StartupGuard refuses to start if it is wired.
 */
@Component
@ConditionalOnProperty(name = "vpn.mfa.stub-enabled", havingValue = "true")
public class LocalStubMfaFactorVerifier implements MfaFactorVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStubMfaFactorVerifier.class);
    private static final byte[] FIXED_TEST_CODE = "000000".getBytes(StandardCharsets.UTF_8);

    @PostConstruct
    void announce() {
        LOGGER.warn("######## LOCAL STUB MFA VERIFIER ACTIVE: accepts the fixed code 000000. NOT REAL MFA. DO NOT DEPLOY. ########");
    }

    @Override
    public boolean verify(UUID tenantId, UUID subjectId, String mfaFactorRef, String code) {
        LOGGER.warn("STUB MFA: verifying with the fixed test code - NOT real MFA");
        if (code == null) {
            return false;
        }
        return MessageDigest.isEqual(FIXED_TEST_CODE, code.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
