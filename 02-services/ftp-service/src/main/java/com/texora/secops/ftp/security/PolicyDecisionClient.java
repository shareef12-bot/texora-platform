package com.texora.secops.ftp.security;

import com.texora.secops.ftp.dto.PolicySummary;
import com.texora.secops.ftp.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the Security Server (SEC) /api/v1/policies for transfer-authorization decisions.
 * FAIL CLOSED: anything other than an explicit ALLOW (timeout, 5xx, malformed body, null) is a DENY.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final RestClient secClient;

    public PolicyDecisionClient(@Qualifier("secRestClient") RestClient secClient) {
        this.secClient = secClient;
    }

    public PolicyDecision decide(UUID tenantId, UUID subject, String action, String resource) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", tenantId.toString());
            body.put("subject", subject.toString());
            body.put("action", action);
            body.put("resource", resource);
            Map<?, ?> response = secClient.post()
                    .uri("/api/v1/policies/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object decision = response == null ? null : response.get("decision");
            if ("ALLOW".equals(decision)) {
                return new PolicyDecision(true, false, "ALLOW");
            }
            return new PolicyDecision(false, false, "POLICY_DENIED");
        } catch (RuntimeException e) {
            LOGGER.warn("Policy decision unavailable - failing closed (DENY): {}", e.toString());
            return new PolicyDecision(false, true, "POLICY_SERVICE_UNAVAILABLE");
        }
    }

    public List<PolicySummary> listPolicies(UUID tenantId) {
        try {
            List<Map<String, Object>> raw = secClient.get()
                    .uri("/api/v1/policies?tenantId={tenantId}", tenantId.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<PolicySummary> result = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> m : raw) {
                    result.add(new PolicySummary(text(m.get("id")), text(m.get("name")), text(m.get("description"))));
                }
            }
            return result;
        } catch (RuntimeException e) {
            LOGGER.warn("Could not list policies from SEC: {}", e.toString());
            throw new UpstreamUnavailableException("POLICY_SERVICE_UNAVAILABLE", "Policy service unavailable", e);
        }
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
