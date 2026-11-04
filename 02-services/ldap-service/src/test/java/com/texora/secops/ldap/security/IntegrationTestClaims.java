package com.texora.secops.ldap.security;

import java.util.UUID;

/**
 * Minimal stand-in for {@code TokenClaims} used in integration tests.
 *
 * <p>Stored as the {@link RbacInterceptor#CLAIMS_ATTR} request attribute by
 * {@link IntegrationTestRbacInterceptor}. Controllers cast the attribute to
 * {@code TokenClaims}; this class must match that interface.
 *
 * <p><b>ASSUMPTION</b>: {@code TokenClaims} is an interface or open class from
 * {@code texora-iam-client}. If it is final, replace this with a Mockito mock
 * configured in a {@code @TestConfiguration}.
 *
 * NOTE: If TokenClaims is an interface in texora-iam-client, implement it here.
 * If it is a concrete class, this must extend it or be replaced with a mock.
 * See "Uncertain shared-library class names" in README.
 */
public class IntegrationTestClaims {

    private final UUID subject;
    private final UUID tenantId;

    public IntegrationTestClaims(UUID subject, UUID tenantId) {
        this.subject  = subject;
        this.tenantId = tenantId;
    }

    public UUID getSubject()  { return subject; }
    public UUID getTenantId() { return tenantId; }

    public boolean hasRole(String role) {
        return true; // all roles granted in integration tests
    }
}
