package com.texora.secops.ftp.admin.service;

import com.texora.secops.ftp.dto.PolicySummary;
import com.texora.secops.ftp.security.PolicyDecisionClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PolicyService {

    private final PolicyDecisionClient policyClient;

    public PolicyService(PolicyDecisionClient policyClient) {
        this.policyClient = policyClient;
    }

    /** Read-only view of the SEC policies that govern this tenant's transfers. */
    public List<PolicySummary> list(UUID tenantId) {
        return policyClient.listPolicies(tenantId);
    }
}
