package com.texora.secops.dc.security;

import com.texora.secops.dc.exception.DcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the Security Server (SEC) for central policy decisions beyond local dc_policy (LLD section 8).
 *
 * <p>Returns true ONLY on an explicit "ALLOW". Anything else (DENY, empty body, timeout, 5xx, unconfigured base URL)
 * is "not allowed" / an exception - fail closed.
 *
 * <p>UNVERIFIED: the SEC endpoint path and body shape below are assumptions (LLD only says "REST /api/v1/policies").
 * Verify against the SEC stub (:8083) / real SEC OpenAPI. mTLS client configuration is NOT wired here - see README.
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);
    private static final String DECISION_PATH = "/api/v1/policies/decisions";

    private final RestClient restClient;
    private final String baseUrl;

    public PolicyDecisionClient(@Value("${dc.sec.base-url:}") String baseUrl,
                                @Value("${dc.sec.timeout-ms:2000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
    }

    public boolean isAllowed(UUID tenantId, String subjectType, String subjectId, String resource, String action) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            LOGGER.warn("SEC consult requested but dc.sec.base-url is not configured - not allowed");
            return false;
        }
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("tenantId", tenantId.toString());
        request.put("subjectType", subjectType);
        request.put("subjectId", subjectId);
        request.put("resource", resource);
        request.put("action", action);
        try {
            Map<?, ?> response = restClient.post()
                    .uri(baseUrl + DECISION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return response != null && "ALLOW".equals(String.valueOf(response.get("decision")));
        } catch (RuntimeException e) {
            LOGGER.warn("SEC policy decision call failed: {}", e.getClass().getSimpleName());
            throw DcException.upstreamTimeout("Security Server decision unavailable");
        }
    }
}
