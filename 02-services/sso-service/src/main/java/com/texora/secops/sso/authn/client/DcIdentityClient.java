package com.texora.secops.sso.authn.client;

import com.texora.secops.sso.authn.model.DcUserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves user/group/role data from Domain Controller (DC LLD §6.1
 * {@code /api/v1/users}, {@code /api/v1/groups}) — or HRMS, per decision
 * D-2 — for token claim population. DC remains system of record; SSO never
 * persists its own copy of this data.
 */
@Component
public class DcIdentityClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcIdentityClient.class);

    private final RestTemplate restTemplate;
    private final String dcServiceBaseUrl;
    private final boolean stubMode;

    public DcIdentityClient(RestTemplate restTemplate,
                             @Value("${texora.sso.dc-service.base-url:}") String dcServiceBaseUrl,
                             @Value("${texora.sso.dc-service.stub-mode:true}") boolean stubMode) {
        this.restTemplate = restTemplate;
        this.dcServiceBaseUrl = dcServiceBaseUrl;
        this.stubMode = stubMode;
    }

    @SuppressWarnings("unchecked")
    public Optional<DcUserProfile> fetchProfile(UUID dcUserId) {
        if (stubMode || dcServiceBaseUrl == null || dcServiceBaseUrl.isBlank()) {
            LOGGER.warn("DcIdentityClient running in stub mode for dcUserId={}", dcUserId);
            return Optional.empty();
        }
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    dcServiceBaseUrl + "/api/v1/users/" + dcUserId, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = response.getBody();
            String username = (String) body.get("username");
            List<UUID> groupIds = ((List<String>) body.getOrDefault("groupIds", List.of()))
                    .stream().map(UUID::fromString).toList();
            List<String> roles = (List<String>) body.getOrDefault("roles", List.of());
            return Optional.of(new DcUserProfile(dcUserId, username, groupIds, roles));
        } catch (RestClientException e) {
            LOGGER.error("DC identity lookup failed for dcUserId={}: {}", dcUserId, e.getMessage());
            return Optional.empty();
        }
    }
}
