package com.texora.secops.sso.consent.service;

import com.texora.secops.sso.domain.SsoConsentGrant;
import com.texora.secops.sso.dto.PolicyResponse;
import com.texora.secops.sso.repository.SsoConsentGrantRepository;
import com.texora.secops.sso.security.PolicyDecisionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the fail-closed contract end-to-end, INCLUDING the case the review
 * flagged: the SEC stub now correctly defaults to DENY when no policy
 * matches. This test asserts ConsentPolicyService also denies on an explicit
 * DENY from SEC — not just on an empty/missing response — so the two
 * behaviours don't silently diverge.
 */
@ExtendWith(MockitoExtension.class)
class ConsentPolicyServiceTest {

    @Mock
    private SsoConsentGrantRepository consentGrantRepository;

    @Mock
    private PolicyDecisionClient policyDecisionClient;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID dcUserId = UUID.randomUUID();

    @Test
    void deniesWhenNoConsentGrantExists() {
        when(consentGrantRepository.findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
                dcUserId, applicationId, tenantId)).thenReturn(Optional.empty());

        ConsentPolicyService service = new ConsentPolicyService(consentGrantRepository, policyDecisionClient);

        assertThat(service.isAccessAllowed(tenantId, applicationId, dcUserId)).isFalse();
    }

    @Test
    void deniesWhenSecReturnsExplicitDeny() {
        when(consentGrantRepository.findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
                dcUserId, applicationId, tenantId)).thenReturn(Optional.of(activeConsent()));
        when(policyDecisionClient.evaluate(tenantId, applicationId, dcUserId))
                .thenReturn(Optional.of(new PolicyResponse(applicationId, "pol-1", false, "no matching policy")));

        ConsentPolicyService service = new ConsentPolicyService(consentGrantRepository, policyDecisionClient);

        assertThat(service.isAccessAllowed(tenantId, applicationId, dcUserId)).isFalse();
    }

    @Test
    void deniesWhenSecReturnsNoDecisionAtAll() {
        when(consentGrantRepository.findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
                dcUserId, applicationId, tenantId)).thenReturn(Optional.of(activeConsent()));
        when(policyDecisionClient.evaluate(tenantId, applicationId, dcUserId)).thenReturn(Optional.empty());

        ConsentPolicyService service = new ConsentPolicyService(consentGrantRepository, policyDecisionClient);

        assertThat(service.isAccessAllowed(tenantId, applicationId, dcUserId)).isFalse();
    }

    @Test
    void allowsOnlyWhenConsentActiveAndSecExplicitlyAllows() {
        when(consentGrantRepository.findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
                dcUserId, applicationId, tenantId)).thenReturn(Optional.of(activeConsent()));
        when(policyDecisionClient.evaluate(tenantId, applicationId, dcUserId))
                .thenReturn(Optional.of(new PolicyResponse(applicationId, "pol-1", true, "matched")));

        ConsentPolicyService service = new ConsentPolicyService(consentGrantRepository, policyDecisionClient);

        assertThat(service.isAccessAllowed(tenantId, applicationId, dcUserId)).isTrue();
    }

    @Test
    void deniesWhenConsentWasRevoked() {
        when(consentGrantRepository.findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(
                dcUserId, applicationId, tenantId)).thenReturn(Optional.empty());

        ConsentPolicyService service = new ConsentPolicyService(consentGrantRepository, policyDecisionClient);

        assertThat(service.isAccessAllowed(tenantId, applicationId, dcUserId)).isFalse();
    }

    private SsoConsentGrant activeConsent() {
        return new SsoConsentGrant(UUID.randomUUID(), tenantId, dcUserId, applicationId, "openid,profile",
                Instant.now(), null);
    }
}
