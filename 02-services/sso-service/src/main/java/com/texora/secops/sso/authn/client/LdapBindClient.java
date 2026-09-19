package com.texora.secops.sso.authn.client;

import com.texora.secops.sso.authn.model.BindResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Calls the LDAP Directory service's PROTECTED BIND endpoint to verify a
 * submitted credential. This class NEVER speaks the LDAP wire protocol
 * itself — that is LDAP Directory's own isolated adapter
 * ({@code LdapEngineAdapter}, per shared standards B.4) — SSO only calls
 * LDAP's control-plane REST bind endpoint over mTLS.
 *
 * Until the LDAP Directory service exists (it is build order #2, this
 * service is #1 per the roadmap), this client is stubbed to always fail
 * closed with a clear "not yet available" reason rather than fabricating a
 * successful bind.
 */
@Component
public class LdapBindClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapBindClient.class);

    private final RestTemplate restTemplate;
    private final String ldapServiceBaseUrl;
    private final boolean stubMode;

    public LdapBindClient(RestTemplate restTemplate,
                           @Value("${texora.sso.ldap-service.base-url:}") String ldapServiceBaseUrl,
                           @Value("${texora.sso.ldap-service.stub-mode:true}") boolean stubMode) {
        this.restTemplate = restTemplate;
        this.ldapServiceBaseUrl = ldapServiceBaseUrl;
        this.stubMode = stubMode;
    }

    public BindResult verifyBind(String username, String credential) {
        if (stubMode || ldapServiceBaseUrl == null || ldapServiceBaseUrl.isBlank()) {
            LOGGER.warn("LdapBindClient running in stub mode; failing closed for username={}", username);
            return BindResult.failure("LDAP_SERVICE_NOT_AVAILABLE");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("username", username, "credential", credential);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ldapServiceBaseUrl + "/api/v1/bind", new HttpEntity<>(body, headers), Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object dcUserId = response.getBody().get("dcUserId");
                if (dcUserId != null) {
                    return BindResult.success(UUID.fromString(dcUserId.toString()));
                }
            }
            return BindResult.failure("BIND_REJECTED");
        } catch (RestClientException e) {
            // Fail closed: any transport/timeout error is a bind failure, never a success.
            LOGGER.error("LDAP bind call failed for username={}: {}", username, e.getMessage());
            return BindResult.failure("LDAP_UNAVAILABLE");
        }
    }
}
