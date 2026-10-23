package com.texora.secops.dc.policy.service;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.PolicyRepository;
import com.texora.secops.dc.security.CallerContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** POST /api/v1/policies. Creating a policy grants nothing by itself - effect only appears through an assignment. */
@Service
public class PolicyManagementService {

    private final PolicyRepository policies;
    private final DcAuditRecorder audit;
    private final TimeSource time;

    public PolicyManagementService(PolicyRepository policies, DcAuditRecorder audit, TimeSource time) {
        this.policies = policies;
        this.audit = audit;
        this.time = time;
    }

    @Transactional
    public DcPolicy create(CallerContext caller, String subjectType, String resource, String action, String effect) {
        DcPolicy policy = new DcPolicy();
        policy.setId(UUID.randomUUID());
        policy.setTenantId(caller.getTenantId());
        policy.setSubjectType(subjectType);
        policy.setResource(resource);
        policy.setAction(action);
        policy.setEffect(effect);
        policy.setVersion(1);
        policy.setCreatedBy(caller.getSubjectId());
        policy.setCreatedAt(time.now());
        policies.save(policy);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("subjectType", subjectType);
        payload.put("resource", resource);
        payload.put("action", action);
        payload.put("effect", effect);
        audit.record(caller.getTenantId(), caller.getSubjectId(), "POLICY_CREATED", "POLICY", policy.getId().toString(), "SUCCESS", payload);
        return policy;
    }
}
