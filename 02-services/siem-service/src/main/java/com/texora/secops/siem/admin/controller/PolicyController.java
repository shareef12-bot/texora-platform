package com.texora.secops.siem.admin.controller;

import com.texora.secops.siem.dto.policy.PolicyResponse;
import com.texora.secops.siem.security.PolicyDecisionClient;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only listing of policies applicable to the caller's tenant, proxied from SEC via the
 *  single PolicyDecisionClient adapter (B.4). A SEC outage surfaces as 504 (see
 *  PolicyDecisionClient/GlobalExceptionHandler), not as an empty list. */
@RestController
public class PolicyController {

    private final PolicyDecisionClient policyDecisionClient;

    public PolicyController(PolicyDecisionClient policyDecisionClient) {
        this.policyDecisionClient = policyDecisionClient;
    }

    @GetMapping("/api/v1/policies")
    @RequiresRole("siem.policy.read")
    public List<PolicyResponse> list() {
        return policyDecisionClient.listApplicablePolicies(CallerContext.currentTenantId());
    }
}
