package com.texora.secops.sso.security;

import com.texora.secops.sso.dto.PolicyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls the Security Server (SEC) policy API for application-access
 * decisions. This is the ONLY class that talks to SEC's policy endpoint —
 * every caller goes through here so the fail-closed contract lives in one
 * place: a missing/ambiguous/erroring response is Optional.empty(), which
 * every caller (ConsentPolicyService) MUST treat as DENY, never as ALLOW.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final RestTemplate restTemplate;
    private final String secServiceBaseUrl;

    public PolicyDecisionClient(RestTemplate restTemplate,
                                 @Value("${texora.sso.sec-service.base-url}") String secServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.secServiceBaseUrl = secServiceBaseUrl;
    }

    /**
     * Returns Optional.empty() whenever a policy decision cannot be
     * positively obtained (no policy found, timeout, malformed response,
     * or any transport error). Callers must treat empty as DENY.
     */
    public Optional<PolicyResponse> evaluate(UUID tenantId, UUID applicationId, UUID dcUserId) {
        try {
            Map<String, Object> request = Map.of(
                    "tenantId", tenantId.toString(),
                    "applicationId", applicationId.toString(),
                    "dcUserId", dcUserId.toString());
            ResponseEntity<PolicyResponse> response = restTemplate.postForEntity(
                    secServiceBaseUrl + "/api/v1/policies/evaluate", request, PolicyResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                LOGGER.warn("SEC policy evaluation returned no usable decision for applicationId={}; "
                        + "treating as DENY (fail-closed)", applicationId);
                return Optional.empty();
            }
            return Optional.of(response.getBody());
        } catch (RestClientException e) {
            LOGGER.error("SEC policy evaluation failed for applicationId={}: {} — failing closed",
                    applicationId, e.getMessage());
            return Optional.empty();
        }
    }
}
