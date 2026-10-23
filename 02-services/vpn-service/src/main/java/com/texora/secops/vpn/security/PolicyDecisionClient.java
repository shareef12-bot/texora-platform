package com.texora.secops.vpn.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.config.VpnProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Calls the Security Server for a policy decision over mTLS (B.6.1). FAIL CLOSED by construction: the only value that
 * can ever lead to access is an exact, well-formed {"decision":"ALLOW"} on an HTTP 200. Timeout, transport error,
 * non-200, oversize/malformed body or any other value is {@link SecDecision#UNAVAILABLE}; an explicit "DENY" is DENY.
 *
 * The SEC contract (POST /api/v1/policies/decisions) is ASSUMED - see README "Unsure about".
 */
@Component
public class PolicyDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionClient.class);
    private static final int MAX_BODY_CHARS = 64 * 1024;

    private final HttpClient httpClient;
    private final VpnProperties properties;
    private final ObjectMapper objectMapper;

    public PolicyDecisionClient(@Qualifier("secHttpClient") HttpClient httpClient, VpnProperties properties,
                                ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param maxAttempts 1 for session establishment (fail fast, user waiting); more for active-session re-validation,
     *                    bounded by vpn.sec.revalidation-deadline-ms so a single blip does not revoke a healthy session
     *                    but an outage still does within a known, short time.
     */
    public SecDecision decide(SecDecisionRequest request, int maxAttempts) {
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(maxAttempts <= 1 ? properties.getSec().getTimeoutMs()
                : properties.getSec().getRevalidationDeadlineMs());
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingMs <= 0) {
                break;
            }
            SecDecision decision = decideOnce(request, Math.min(properties.getSec().getTimeoutMs(), remainingMs));
            if (decision != SecDecision.UNAVAILABLE) {
                return decision; // ALLOW and DENY are both final answers
            }
        }
        return SecDecision.UNAVAILABLE;
    }

    private SecDecision decideOnce(SecDecisionRequest request, long timeoutMs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", request.getTenantId().toString());
            body.put("subjectId", request.getSubjectId().toString());
            body.put("action", request.getAction());
            body.put("resource", request.getResource());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.getSec().getBaseUrl() + "/api/v1/policies/decisions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .get(timeoutMs + 250, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200 || response.body() == null || response.body().length() > MAX_BODY_CHARS) {
                LOGGER.warn("SEC decision unusable: status={}", response.statusCode());
                return SecDecision.UNAVAILABLE;
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode decision = node == null ? null : node.get("decision");
            if (decision == null || !decision.isTextual()) {
                return SecDecision.UNAVAILABLE;
            }
            switch (decision.asText()) {
                case "ALLOW":
                    return SecDecision.ALLOW;
                case "DENY":
                    return SecDecision.DENY;
                default:
                    return SecDecision.UNAVAILABLE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SecDecision.UNAVAILABLE;
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("SEC call failed: {}", e.getClass().getSimpleName());
            return SecDecision.UNAVAILABLE;
        } catch (Exception e) {
            LOGGER.warn("SEC call error: {}", e.getClass().getSimpleName());
            return SecDecision.UNAVAILABLE; // never an exception path that reads as success
        }
    }
}
