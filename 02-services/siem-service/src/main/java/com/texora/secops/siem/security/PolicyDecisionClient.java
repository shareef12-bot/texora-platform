package com.texora.secops.siem.security;

import com.texora.secops.siem.dto.policy.PolicyResponse;
import com.texora.secops.siem.web.UpstreamTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The sole point of contact with the Security Server (SEC) for policy decisions and the policy
 * catalog (Integration Points §8 of the LLD). No other class in this service talks to SEC.
 *
 * <p>Every failure mode on {@link #evaluate} - timeout, non-2xx, connection refused, malformed
 * response - resolves to {@link PolicyDecision#deny}: "any ambiguity, missing policy, timeout,
 * or internal error results in DENY. Never let an exception path be interpreted as success"
 * (B.6.3). {@link #listApplicablePolicies} is a plain read rather than a security decision, so
 * its failure surfaces as an upstream timeout (504) instead of silently returning an empty list.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final RestClient restClient;

    public PolicyDecisionClient(@Value("${siem.sec.base-url}") String secBaseUrl,
                                 @Value("${siem.sec.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(secBaseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Gate a sensitive operation (e.g. tenant-scoped search) on a live policy check. */
    public PolicyDecision evaluate(UUID tenantId, String action, String resourceScope) {
        try {
            EvaluatePolicyRequest request = new EvaluatePolicyRequest(tenantId, action, resourceScope);
            EvaluatePolicyResponse response = restClient.post()
                    .uri("/api/v1/policies/evaluate")
                    .body(request)
                    .retrieve()
                    .body(EvaluatePolicyResponse.class);

            if (response == null) {
                // Ambiguous response body - treat exactly like a failure, not like a pass.
                return PolicyDecision.deny("SEC returned no decision for action " + action);
            }
            return response.isAllowed()
                    ? PolicyDecision.allow("SEC policy evaluation allowed " + action)
                    : PolicyDecision.deny("SEC policy evaluation denied " + action);
        } catch (Exception ex) {
            LOGGER.error("Policy evaluation call to SEC failed for tenant {} action {} - failing closed",
                    tenantId, action, ex);
            return PolicyDecision.deny("SEC unavailable: " + ex.getMessage());
        }
    }

    /** Read-only listing for GET /api/v1/policies. */
    public List<PolicyResponse> listApplicablePolicies(UUID tenantId) {
        try {
            PolicyResponse[] policies = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/policies").queryParam("tenantId", tenantId).build())
                    .retrieve()
                    .body(PolicyResponse[].class);
            return policies == null ? List.of() : List.of(policies);
        } catch (Exception ex) {
            throw new UpstreamTimeoutException("Failed to list policies from SEC for tenant " + tenantId, ex);
        }
    }
}