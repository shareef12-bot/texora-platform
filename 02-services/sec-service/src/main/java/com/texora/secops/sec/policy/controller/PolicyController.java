package com.texora.secops.sec.policy.controller;

import com.texora.secops.sec.policy.domain.SecPolicy;
import com.texora.secops.sec.policy.dto.PolicyEvaluationRequest;
import com.texora.secops.sec.policy.dto.PolicyEvaluationResponse;
import com.texora.secops.sec.policy.service.PolicyEvaluationService;
import com.texora.secops.sec.policy.service.PolicyLifecycleService;
import com.texora.secops.sec.security.TenantContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for policy evaluation and listing.
 * POST /api/v1/policies/evaluate — mTLS service auth (most-called endpoint)
 * GET  /api/v1/policies          — sec.policy.read
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyEvaluationService evaluationService;
    private final PolicyLifecycleService lifecycleService;

    public PolicyController(PolicyEvaluationService evaluationService,
                            PolicyLifecycleService lifecycleService) {
        this.evaluationService = evaluationService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Policy evaluation endpoint — the most-called API in the platform.
     * Called by DC, LDAP, SSO, FTP, CGI, SIEM, and VPN over mTLS.
     * Authentication: ServiceAuthInterceptor (mTLS, not SSO token).
     */
    @PostMapping("/evaluate")
    public ResponseEntity<PolicyEvaluationResponse> evaluate(
            @Valid @RequestBody PolicyEvaluationRequest request) {

        UUID tenantId = TenantContext.requireTenantId();
        LOGGER.debug("Policy evaluate: caller={} resource={} action={}",
                request.getCallerService(), request.getResource(), request.getAction());

        PolicyEvaluationResponse response = evaluationService.evaluate(tenantId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * List all policies for this tenant.
     * Requires role: sec.policy.read
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sec.policy.read')")
    public ResponseEntity<Page<SecPolicy>> listPolicies(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(lifecycleService.listPolicies(tenantId, pageable));
    }
}
