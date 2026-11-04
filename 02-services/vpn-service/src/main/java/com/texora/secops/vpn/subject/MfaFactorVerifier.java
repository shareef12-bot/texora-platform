package com.texora.secops.vpn.subject;

import java.util.UUID;

/**
 * Verifies an MFA code against an enrolled factor reference. There is NO shared-library MFA class
 * (KNOWN-INTEGRATION-PITFALLS #1), so this is a local port. Only a clearly-flagged stub exists today.
 */
public interface MfaFactorVerifier {

    /** Must return false (never throw success) on any error. The code is never logged or stored. */
    boolean verify(UUID tenantId, UUID subjectId, String mfaFactorRef, String code);

    boolean isStub();
}
