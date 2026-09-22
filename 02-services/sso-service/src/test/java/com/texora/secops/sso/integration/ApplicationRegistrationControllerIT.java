package com.texora.secops.sso.integration;

import com.texora.secops.sso.domain.Protocol;
import com.texora.secops.sso.dto.ApplicationRegistrationRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: unauthenticated caller must get 401, not a silently-empty 200,
 * on the application-registration endpoint (shared standards B.6.1/B.6.3).
 *
 * Disabled by default in this deliverable because it depends on the real
 * Platform Foundation JARs (texora-secops-starter / texora-iam-client) being
 * resolvable from the build's local/remote Maven repo, which this container
 * does not have network access to. Re-enable once those coordinates resolve
 * in CI.
 */
@Disabled("requires resolvable texora-secops-platform Platform Foundation artifacts")
class ApplicationRegistrationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectsApplicationRegistrationWithoutABearerToken() {
        ApplicationRegistrationRequest request = new ApplicationRegistrationRequest();
        request.setName("Ilmora");
        request.setProductId("ilmora");
        request.setProtocol(Protocol.OIDC);
        request.setRedirectUris(List.of("https://ilmora.texora.com/callback"));

        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/application-registration",
                new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
