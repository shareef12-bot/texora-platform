package com.texora.secops.sso.authn.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin wrapper around the shared IAM client library's MFA verifier
 * (com.texora.secops.iam.MfaVerifier, from texora-iam-client — Platform
 * Foundation). Kept as its own injectable component, rather than calling the
 * static library method directly from LoginOrchestrationService, purely so
 * the login orchestration flow stays unit-testable without needing to mock
 * a static method. Implements NO verification logic itself — per the
 * CRITICAL CONSTRAINT, factor verification (TOTP/push/etc.) is entirely the
 * shared library's responsibility.
 */
@Component
public class MfaVerificationClient {

    public boolean verify(UUID dcUserId, String factorType, String submittedCode) {
        return com.texora.secops.iam.MfaVerifier.verify(dcUserId, factorType, submittedCode);
    }
}
