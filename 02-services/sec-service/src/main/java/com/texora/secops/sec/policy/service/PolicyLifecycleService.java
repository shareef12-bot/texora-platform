package com.texora.secops.sec.policy.service;

import com.texora.secops.sec.approval.service.PrivilegedApprovalService;
import com.texora.secops.sec.policy.domain.SecPolicy;
import com.texora.secops.sec.policy.domain.SecPolicyVersion;
import com.texora.secops.sec.policy.repository.SecPolicyRepository;
import com.texora.secops.sec.policy.repository.SecPolicyVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyLifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyLifecycleService.class);

    private final SecPolicyRepository policyRepository;
    private final SecPolicyVersionRepository versionRepository;
    private final PrivilegedApprovalService approvalService;

    public PolicyLifecycleService(SecPolicyRepository policyRepository,
                                   SecPolicyVersionRepository versionRepository,
                                   PrivilegedApprovalService approvalService) {
        this.policyRepository = policyRepository;
        this.versionRepository = versionRepository;
        this.approvalService = approvalService;
    }

    @Transactional(readOnly = true)
    public Page<SecPolicy> listPolicies(UUID tenantId, Pageable pageable) {
        return policyRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public SecPolicy getPolicy(UUID tenantId, UUID policyId) {
        return policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found: " + policyId));
    }

    @Transactional
    public SecPolicy createPolicy(UUID tenantId, String name, String subjectType,
                                   String resource, String action, String ownerTeam) {
        SecPolicy policy = new SecPolicy(
                UUID.randomUUID(), tenantId, name, subjectType, resource, action, ownerTeam);
        SecPolicy saved = policyRepository.save(policy);
        LOGGER.info("Created policy id={} name='{}' tenant={}", saved.getId(), name, tenantId);
        return saved;
    }

    @Transactional
    public SecPolicyVersion createPolicyVersion(UUID tenantId, UUID policyId,
                                                 String effect, Map<String, Object> conditions,
                                                 UUID authoredBy) {
        // Verify policy exists and belongs to this tenant
        policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found: " + policyId));

        int nextVersion = versionRepository.findMaxVersionNumber(policyId)
                .map(max -> max + 1)
                .orElse(1);

        SecPolicyVersion version = new SecPolicyVersion(
                UUID.randomUUID(), policyId, tenantId, nextVersion,
                effect, conditions, authoredBy);

        // Draft → PENDING_APPROVAL: route through dual control for activation
        version.setStatus(SecPolicyVersion.STATUS_PENDING_APPROVAL);
        SecPolicyVersion saved = versionRepository.save(version);

        LOGGER.info("Created policy version id={} policyId={} version={} effect={}",
                saved.getId(), policyId, nextVersion, effect);

        // Submit privileged request for the activation approval
        approvalService.submitRequest(
                tenantId,
                "POLICY_ACTIVATION",
                "policy-version:" + saved.getId().toString(),
                authoredBy,
                "Requesting activation of policy version " + nextVersion + " for policy " + policyId
        );

        return saved;
    }

    @Transactional
    public SecPolicyVersion activateVersion(UUID tenantId, UUID versionId, UUID activatedBy) {
        SecPolicyVersion version = versionRepository.findByIdAndTenantId(versionId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy version not found: " + versionId));

        if (!SecPolicyVersion.STATUS_PENDING_APPROVAL.equals(version.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only PENDING_APPROVAL versions can be activated, current status: " + version.getStatus());
        }

        // Verify approval exists before activating
        boolean approved = approvalService.isApproved(
                tenantId, "POLICY_ACTIVATION", "policy-version:" + versionId);
        if (!approved) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Policy version requires dual-control approval before activation");
        }

        // Retire the currently active version for the same policy/resource/action
        versionRepository.findActiveVersionsForEvaluation(
                tenantId,
                getPolicyResource(tenantId, version.getPolicyId()),
                getPolicyAction(tenantId, version.getPolicyId())
        ).forEach(active -> {
            active.setStatus(SecPolicyVersion.STATUS_RETIRED);
            active.setRetiredAt(Instant.now());
            versionRepository.save(active);
        });

        version.setStatus(SecPolicyVersion.STATUS_ACTIVE);
        version.setActivatedAt(Instant.now());
        SecPolicyVersion saved = versionRepository.save(version);

        LOGGER.info("Activated policy version id={} by actor={}", versionId, activatedBy);
        return saved;
    }

    private String getPolicyResource(UUID tenantId, UUID policyId) {
        return policyRepository.findByIdAndTenantId(policyId, tenantId)
                .map(SecPolicy::getResource)
                .orElse("");
    }

    private String getPolicyAction(UUID tenantId, UUID policyId) {
        return policyRepository.findByIdAndTenantId(policyId, tenantId)
                .map(SecPolicy::getAction)
                .orElse("");
    }
}
