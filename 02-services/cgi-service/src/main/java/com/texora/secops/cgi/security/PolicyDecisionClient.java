package com.texora.secops.cgi.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls the Security Server: {@code POST /api/v1/policies/evaluate} (the real SEC stub endpoint).
 *
 * <p>ASSUMED contract (the only part not confirmed - see README): request
 * {@code {tenantId, subject, roles[], action, resource}}, response {@code {"decision":"ALLOW"|"DENY"}}.
 *
 * <p>FAIL CLOSED: this method never throws and never returns ALLOW unless SEC answered 2xx with
 * {@code decision == "ALLOW"}. Timeouts, connection errors, non-2xx, empty/garbled bodies, unknown decision
 * values -> DENY. No request parameters or body are ever sent to SEC.
 */
@Component
public class PolicyDecisionClient {

    public static final String EVALUATE_PATH = "/api/v1/policies/evaluate";

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);

    private final RestClient restClient;

    public PolicyDecisionClient(@Qualifier("secRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public PolicyDecision evaluate(CallerContext caller, String action, String resource) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", caller.getTenantId().toString());
            body.put("subject", caller.getSubject().toString());
            body.put("roles", new ArrayList<>(caller.getRoles()));
            body.put("action", action);
            body.put("resource", resource);

            JsonNode response = restClient.post()
                    .uri(EVALUATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return interpret(response);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                LOGGER.warn("SEC policy evaluation timed out - DENY");
                return PolicyDecision.deny(PolicyDecision.REASON_SEC_TIMEOUT);
            }
            LOGGER.warn("SEC unreachable ({}) - DENY", e.getClass().getSimpleName());
            return PolicyDecision.deny(PolicyDecision.REASON_SEC_UNREACHABLE);
        } catch (RuntimeException e) {
            LOGGER.warn("SEC policy evaluation failed ({}) - DENY", e.getClass().getSimpleName());
            return PolicyDecision.deny(PolicyDecision.REASON_SEC_ERROR);
        }
    }

    private static PolicyDecision interpret(JsonNode response) {
        if (response == null || !response.hasNonNull("decision") || !response.get("decision").isTextual()) {
            return PolicyDecision.deny(PolicyDecision.REASON_SEC_MALFORMED);
        }
        return "ALLOW".equalsIgnoreCase(response.get("decision").asText())
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDecision.REASON_SEC_DENY);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
