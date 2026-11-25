package com.texora.secops.sso.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.dto.PolicyResponse;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls SEC's POST /api/v1/policies/evaluate using SEC's contract
 * (callerService, subject, resource, action). Fail-closed: anything unclear
 * returns Optional.empty(), which callers treat as DENY.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);
    private static final String CALLER_SERVICE = "sso";
    private static final String ACTION_LOGIN = "login";

    private final RestTemplate restTemplate;
    private final String secServiceBaseUrl;
    private final SsoApplicationRepository applicationRepository;

    public PolicyDecisionClient(RestTemplate restTemplate,
                                @Value("${texora.sso.sec-service.base-url}") String secServiceBaseUrl,
                                SsoApplicationRepository applicationRepository) {
        this.restTemplate = restTemplate;
        this.secServiceBaseUrl = secServiceBaseUrl;
        this.applicationRepository = applicationRepository;
    }

    public Optional<PolicyResponse> evaluate(UUID tenantId, UUID applicationId, UUID dcUserId) {
        try {
            Optional<SsoApplication> application = applicationRepository.findById(applicationId);
            if (application.isEmpty()) {
                LOGGER.warn("No application found for applicationId={}; treating as DENY", applicationId);
                return Optional.empty();
            }

            Map<String, Object> request = Map.of(
                    "callerService", CALLER_SERVICE,
                    "subject", Map.of("type", "USER", "id", dcUserId.toString()),
                    "resource", "application:" + application.get().getClientId(),
                    "action", ACTION_LOGIN);

            ResponseEntity<SecDecision> response = restTemplate.postForEntity(
                    secServiceBaseUrl + "/api/v1/policies/evaluate", request, SecDecision.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || response.getBody().getDecision() == null) {
                LOGGER.warn("SEC returned no usable decision for applicationId={}; treating as DENY",
                        applicationId);
                return Optional.empty();
            }

            SecDecision body = response.getBody();
            boolean allowed = "ALLOW".equals(body.getDecision());
            return Optional.of(new PolicyResponse(applicationId, body.getPolicyVersionId(),
                    allowed, body.getReason()));
        } catch (Exception e) {
            LOGGER.error("SEC policy evaluation failed for applicationId={}: {} - failing closed",
                    applicationId, e.getMessage());
            return Optional.empty();
        }
    }

    /** SEC's answer shape. Unknown fields are ignored so SEC can add fields safely. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SecDecision {
        private String decision;
        private String reason;
        private String policyVersionId;

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getPolicyVersionId() { return policyVersionId; }
        public void setPolicyVersionId(String policyVersionId) { this.policyVersionId = policyVersionId; }
    }
}
