package com.texora.secops.vpn.subject;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a dc_user from the Domain Controller (GET /api/v1/users/{id}, DC LLD s6.1) over mTLS.
 * Never throws: anything that is not a clear answer is {@link DcIdentityResult#UNAVAILABLE}.
 * The DC response shape ({"status":"ACTIVE"}) is ASSUMED - see README "Unsure about".
 */
@Component
public class DcIdentityClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcIdentityClient.class);

    private final HttpClient httpClient;
    private final VpnProperties properties;
    private final ObjectMapper objectMapper;

    public DcIdentityClient(@Qualifier("dcHttpClient") HttpClient httpClient, VpnProperties properties, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DcIdentityResult lookup(UUID tenantId, UUID dcUserId) {
        long timeoutMs = properties.getDc().getTimeoutMs();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getDc().getBaseUrl() + "/api/v1/users/" + dcUserId))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .get(timeoutMs + 250, TimeUnit.MILLISECONDS);
            if (response.statusCode() == 404) {
                return DcIdentityResult.NOT_FOUND;
            }
            if (response.statusCode() != 200 || response.body() == null || response.body().length() > 64 * 1024) {
                return DcIdentityResult.UNAVAILABLE;
            }
            JsonNode status = objectMapper.readTree(response.body()).get("status");
            if (status == null || !status.isTextual()) {
                return DcIdentityResult.UNAVAILABLE;
            }
            return "ACTIVE".equals(status.asText()) ? DcIdentityResult.ACTIVE : DcIdentityResult.INACTIVE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DcIdentityResult.UNAVAILABLE;
        } catch (Exception e) {
            LOGGER.warn("DC identity lookup failed: {}", e.getClass().getSimpleName());
            return DcIdentityResult.UNAVAILABLE;
        }
    }
}
