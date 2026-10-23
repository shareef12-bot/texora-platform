package com.texora.secops.dc.policy.service;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroupMembership;
import com.texora.secops.dc.domain.DcPolicy;
import com.texora.secops.dc.domain.DcPolicyAssignment;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.policy.dto.PolicyDecision;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.PolicyAssignmentRepository;
import com.texora.secops.dc.repository.PolicyRepository;
import com.texora.secops.dc.security.PolicyDecisionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FAIL-CLOSED policy evaluation (LLD 7.2, DC_TDD "Deny on policy uncertainty").
 *
 * <p>Result is ALLOW only when at least one applicable policy matches AND every matching policy says ALLOW.
 * It is DENY when: nothing matches, matching policies conflict (any ALLOW together with any DENY), a matching policy
 * says DENY, the request is malformed, the optional SEC confirmation denies/fails, ANY exception occurs, or the
 * audit event cannot be written. A policy-decision audit event is written ALWAYS, regardless of outcome.
 */
@Service
public class PolicyEvaluationService {

    public static final String ALLOW = "ALLOW";
    public static final String DENY = "DENY";

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyEvaluationService.class);

    private final PolicyRepository policies;
    private final PolicyAssignmentRepository assignments;
    private final MembershipRepository memberships;
    private final PolicyDecisionClient secClient;
    private final DcAuditRecorder audit;
    private final boolean secConsultEnabled;

    public PolicyEvaluationService(PolicyRepository policies, PolicyAssignmentRepository assignments,
                                   MembershipRepository memberships, PolicyDecisionClient secClient, DcAuditRecorder audit,
                                   @Value("${dc.policy.sec-consult-enabled:false}") boolean secConsultEnabled) {
        this.policies = policies;
        this.assignments = assignments;
        this.memberships = memberships;
        this.secClient = secClient;
        this.audit = audit;
        this.secConsultEnabled = secConsultEnabled;
    }

    @Transactional(readOnly = true)
    public PolicyDecision evaluate(UUID tenantId, UUID requesterId, String subjectType, String subjectId,
                                   String resource, String action) {
        PolicyDecision decision;
        try {
            decision = decide(tenantId, subjectType, subjectId, resource, action);
        } catch (RuntimeException e) {
            LOGGER.error("Policy evaluation error - failing closed", e);
            decision = deny("EVALUATION_ERROR", Collections.<UUID>emptyList());
        }
        try {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("subjectType", subjectType);
            payload.put("subjectId", subjectId);
            payload.put("resource", resource);
            payload.put("action", action);
            payload.put("decision", decision.getDecision());
            payload.put("reason", decision.getReason());
            payload.put("matchedPolicyIds", toStrings(decision.getMatchedPolicyIds()));
            audit.recordIndependent(tenantId, requesterId, "POLICY_DECISION", "POLICY_EVALUATION",
                    subjectType + ":" + subjectId, decision.getDecision(), payload);
        } catch (RuntimeException e) {
            LOGGER.error("Could not audit policy decision - failing closed", e);
            return deny("AUDIT_UNAVAILABLE", Collections.<UUID>emptyList());
        }
        return decision;
    }

    private PolicyDecision decide(UUID tenantId, String subjectType, String subjectId, String resource, String action) {
        if (isBlank(subjectType) || isBlank(subjectId) || isBlank(resource) || isBlank(action)) {
            return deny("INVALID_REQUEST", Collections.<UUID>emptyList());
        }
        Map<String, Set<String>> subjects = new HashMap<String, Set<String>>();
        subjects.put(subjectType, new HashSet<String>(Collections.singletonList(subjectId)));
        if (DcConstants.SUBJECT_USER.equals(subjectType)) {
            UUID userId;
            try {
                userId = UUID.fromString(subjectId);
            } catch (IllegalArgumentException e) {
                return deny("INVALID_REQUEST", Collections.<UUID>emptyList());
            }
            Set<String> groupIds = new HashSet<String>();
            for (DcGroupMembership m : memberships.findByUser(tenantId, userId)) {
                groupIds.add(m.getId().getGroupId().toString());
            }
            if (!groupIds.isEmpty()) {
                subjects.put(DcConstants.SUBJECT_GROUP, groupIds);
            }
        }
        Set<UUID> policyIds = new HashSet<UUID>();
        for (Map.Entry<String, Set<String>> entry : subjects.entrySet()) {
            for (DcPolicyAssignment a : assignments.findByTenantIdAndSubjectTypeAndSubjectIdIn(tenantId, entry.getKey(), entry.getValue())) {
                policyIds.add(a.getPolicyId());
            }
        }
        if (policyIds.isEmpty()) {
            return deny("NO_MATCHING_POLICY", Collections.<UUID>emptyList());
        }
        List<UUID> matched = new ArrayList<UUID>();
        boolean anyAllow = false;
        boolean anyDeny = false;
        for (DcPolicy p : policies.findByTenantIdAndIdIn(tenantId, policyIds)) {
            if (matches(p.getResource(), resource) && matches(p.getAction(), action)) {
                matched.add(p.getId());
                if (ALLOW.equals(p.getEffect())) {
                    anyAllow = true;
                } else {
                    anyDeny = true; // any value other than ALLOW is treated as DENY
                }
            }
        }
        if (matched.isEmpty()) {
            return deny("NO_MATCHING_POLICY", matched);
        }
        if (anyAllow && anyDeny) {
            return deny("CONFLICTING_POLICIES", matched);
        }
        if (anyDeny) {
            return deny("EXPLICIT_DENY", matched);
        }
        if (secConsultEnabled) {
            try {
                if (!secClient.isAllowed(tenantId, subjectType, subjectId, resource, action)) {
                    return deny("SEC_DENIED", matched);
                }
            } catch (DcException e) {
                return deny("SEC_UNAVAILABLE", matched);
            }
        }
        return new PolicyDecision(ALLOW, "POLICY_ALLOW", matched);
    }

    /** Exact match, "*" for everything, or a trailing-"*" prefix wildcard. */
    static boolean matches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        if ("*".equals(pattern) || pattern.equals(value)) {
            return true;
        }
        return pattern.endsWith("*") && value.startsWith(pattern.substring(0, pattern.length() - 1));
    }

    private PolicyDecision deny(String reason, List<UUID> matched) {
        return new PolicyDecision(DENY, reason, matched);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private List<String> toStrings(List<UUID> ids) {
        List<String> out = new ArrayList<String>();
        if (ids != null) {
            for (UUID id : ids) {
                out.add(id.toString());
            }
        }
        return out;
    }
}
