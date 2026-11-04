package com.texora.qap.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Calls the SEC service for RBAC policy evaluation.
 * POST /api/v1/policies/evaluate
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final WebClient webClient;

    public PolicyDecisionClient(
            @Value("${qap.sec.base-url:http://sec-service}") String secBaseUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(secBaseUrl).build();
    }

    /**
     * @return true if the principal is permitted, false otherwise.
     *         Fail-closed: any error returns false.
     */
    public boolean evaluate(String principal, String resource, String action, String tenantId) {
        try {
            Map<String, String> body = Map.of(
                    "principal", principal,
                    "resource", resource,
                    "action", action,
                    "tenantId", tenantId
            );

            PolicyEvaluationResponse response = webClient.post()
                    .uri("/api/v1/policies/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(PolicyEvaluationResponse.class)
                    .block();

            return response != null && response.isAllowed();
        } catch (Exception ex) {
            LOGGER.warn("Policy evaluation failed for principal={} resource={} action={} — denying (fail-closed): {}",
                    principal, resource, action, ex.getMessage());
            return false;
        }
    }

    /** Inner DTO for policy response. */
    public static class PolicyEvaluationResponse {
        private boolean allowed;

        public PolicyEvaluationResponse() {}

        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
    }
}
