package com.texora.secops.sso.consent.service;

import com.texora.secops.sso.dto.PolicyResponse;
import com.texora.secops.sso.domain.SsoConsentGrant;
import com.texora.secops.sso.repository.SsoConsentGrantRepository;
import com.texora.secops.sso.security.PolicyDecisionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Enforces deny-if-policy-or-consent-missing for application access
 * (LLD §5.3, SSO_TDD 'Consent/Policy' row). This mirrors the fail-closed
 * pattern in DC's PolicyEvaluationService: ANY ambiguity — no consent row,
 * a revoked consent, or SEC returning no usable policy decision — is DENY.
 *
 * IMPORTANT: as of the SEC stub fix, the stub itself now defaults to DENY
 * when no policy matches (it used to default-permit, which was wrong). This
 * service must not compensate for that by defaulting to ALLOW on an empty
 * SEC response — it must treat an empty/negative SEC response as DENY here
 * too, so the fail-closed guarantee holds end-to-end regardless of what the
 * SEC stub does internally.
 */
@Service
public class ConsentPolicyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsentPolicyService.class);

    private final SsoConsentGrantRepository consentGrantRepository;
    private final PolicyDecisionClient policyDecisionClient;

    public ConsentPolicyService(SsoConsentGrantRepository consentGrantRepository,
                                 PolicyDecisionClient policyDecisionClient) {
        this.consentGrantRepository = consentGrantRepository;
        this.policyDecisionClient = policyDecisionClient;
    }

    /**
     * @return true only when an active consent grant exists AND SEC
     *         explicitly returned an ALLOW decision. Every other path —
     *         missing consent, revoked consent, SEC returning empty, SEC
     *         returning an explicit DENY — returns false.
     */
    public boolean isAccessAllowed(UUID tenantId, UUID applicationId, UUID dcUserId) {
        Optional<SsoConsentGrant> consent = consentGrantRepository
                .findByDcUserIdAndApplicationIdAndTenantIdAndRevokedAtIsNull(dcUserId, applicationId, tenantId);

        if (consent.isEmpty()) {
            LOGGER.info("No active consent grant for dcUserId={} applicationId={} — denying (fail-closed)",
                    dcUserId, applicationId);
            return false;
        }

        Optional<PolicyResponse> policy = policyDecisionClient.evaluate(tenantId, applicationId, dcUserId);
        if (policy.isEmpty()) {
            LOGGER.info("No policy decision returned by SEC for applicationId={} — denying (fail-closed)",
                    applicationId);
            return false;
        }

        // Explicit check, not a default: only a positive, present ALLOW counts.
        boolean allowed = policy.get().isAllowed();
        if (!allowed) {
            LOGGER.info("SEC returned DENY for applicationId={} dcUserId={}: {}", applicationId, dcUserId,
                    policy.get().getReason());
        }
        return allowed;
    }
}
