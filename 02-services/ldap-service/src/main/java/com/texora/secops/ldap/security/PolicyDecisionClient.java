package com.texora.secops.ldap.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Calls the Security Server (SEC) policy endpoint to obtain a policy decision
 * for schema-change and bind-config approval operations.
 *
 * <p>Fail-closed: if SEC is unreachable or returns anything other than PERMIT,
 * the decision is DENY.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final RestClient restClient;
    private final String policyEndpoint;

    public PolicyDecisionClient(@Value("${ldap.sec.policy-endpoint}") String policyEndpoint) {
        this.policyEndpoint = policyEndpoint;
        this.restClient = RestClient.builder()
                .baseUrl(policyEndpoint)
                .build();
    }

    /**
     * Asks SEC whether the given action is permitted for the given subject.
     *
     * @param subjectId  UUID of the acting subject
     * @param action     action string (e.g. {@code ldap.schema.approve})
     * @param resourceId resource being acted upon
     * @return {@code true} only if SEC explicitly returns PERMIT
     */
    public boolean isPermitted(String subjectId, String action, String resourceId) {
        try {
            var body = Map.of(
                    "subjectId", subjectId,
                    "action", action,
                    "resourceId", resourceId
            );
            var response = restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object decision = response.getBody().get("decision");
                return "PERMIT".equalsIgnoreCase(String.valueOf(decision));
            }
            LOGGER.warn("LDAP PolicyDecisionClient: unexpected response {} from SEC — denying",
                        response.getStatusCode());
            return false;

        } catch (RestClientException e) {
            LOGGER.error("LDAP PolicyDecisionClient: SEC unreachable for action='{}' subjectId='{}' — failing closed",
                         action, subjectId, e);
            return false; // fail closed — deny on any exception
        }
    }
}
