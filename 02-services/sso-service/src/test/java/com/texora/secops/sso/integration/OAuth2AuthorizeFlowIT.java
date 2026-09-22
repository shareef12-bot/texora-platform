package com.texora.secops.sso.integration;

import com.texora.secops.sso.authn.client.LdapBindClient;

import com.texora.secops.sso.authn.client.MfaVerificationClient;
import com.texora.secops.sso.authn.model.BindResult;
import com.texora.secops.sso.domain.*;
import com.texora.secops.sso.dto.PolicyResponse;
import com.texora.secops.sso.repository.*;
import com.texora.secops.sso.security.PolicyDecisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end: registers a real (test) application, drives an actual browser
 * -style /oauth2/authorize round trip — including our custom /login page
 * calling LoginOrchestrationService — and confirms a real authorization
 * code comes back and exchanges for a real access token at /oauth2/token.
 * This is deliberately NOT a mock of LoginOrchestrationService: only the
 * three external systems it can't reach in a test sandbox (LDAP, MFA
 * factor verification, SEC policy) are replaced with fixed-answer test
 * doubles. Everything else — RegisteredClientRepository resolution,
 * TenantContext handling, session/token persistence, the Authorization
 * Server's own code issuance and signing — runs for real.
 *
 * Disabled here for the same reason as ApplicationRegistrationControllerIT:
 * it needs the real Platform Foundation JARs resolvable in CI. The flow
 * itself is otherwise complete and ready to run once those resolve.
 */
@Disabled("requires resolvable texora-secops-platform Platform Foundation artifacts")
class OAuth2AuthorizeFlowIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class TestDoubles {

        @Bean
        @Primary
        LdapBindClient ldapBindClient() {
            LdapBindClient stub = mock(LdapBindClient.class);
            when(stub.verifyBind("alice", "correct-horse-battery-staple"))
                    .thenReturn(BindResult.success(TEST_DC_USER_ID));
            return stub;
        }

        @Bean
        @Primary
        MfaVerificationClient mfaVerificationClient() {
            MfaVerificationClient stub = mock(MfaVerificationClient.class);
            when(stub.verify(org.mockito.ArgumentMatchers.eq(TEST_DC_USER_ID), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(true);
            return stub;
        }

        @Bean
        @Primary
        PolicyDecisionClient policyDecisionClient() {
            PolicyDecisionClient stub = mock(PolicyDecisionClient.class);
            when(stub.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(TEST_DC_USER_ID)))
                    .thenAnswer(invocation -> Optional.of(new PolicyResponse(
                            invocation.getArgument(1), "pol-test", true, "test allow")));
            return stub;
        }
    }

    private static final UUID TEST_DC_USER_ID = UUID.randomUUID();
    private static final String REDIRECT_URI = "https://client.example.com/callback";

    @LocalServerPort
    private int port;

    @Autowired private SsoApplicationRepository applicationRepository;
    @Autowired private SsoOidcSamlConfigRepository configRepository;
    @Autowired private SsoMfaEnrollmentRepository mfaEnrollmentRepository;
    @Autowired private SsoConsentGrantRepository consentGrantRepository;

    private String clientId;
    private UUID applicationId;
    private RestTemplate noRedirectClient;

    @BeforeEach
    void seedRegisteredApplication() {
        UUID tenantId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        clientId = "ilmora-" + applicationId;

        SsoApplication application = new SsoApplication(applicationId, tenantId, clientId, "Ilmora", "ilmora",
                ApplicationStatus.ACTIVE, UUID.randomUUID(), Instant.now());
        applicationRepository.save(application);

        SsoOidcSamlConfig config = new SsoOidcSamlConfig(UUID.randomUUID(), tenantId, applicationId,
                Protocol.OIDC, REDIRECT_URI, "sec://test");
        configRepository.save(config);

        mfaEnrollmentRepository.save(new SsoMfaEnrollment(UUID.randomUUID(), tenantId, TEST_DC_USER_ID, "TOTP",
                MfaFactorStatus.ACTIVE, Instant.now()));

        consentGrantRepository.save(new SsoConsentGrant(UUID.randomUUID(), tenantId, TEST_DC_USER_ID, applicationId,
                "openid,profile", Instant.now(), null));

        noRedirectClient = new RestTemplateBuilder()
                .requestFactory(() -> new NoRedirectRequestFactory())
                .rootUri("http://localhost:" + port)
                .build();
    }

    @Test
    void fullAuthorizationCodeFlowIssuesARealTokenViaLoginOrchestrationService() throws Exception {
        String codeVerifier = "test-code-verifier-1234567890-1234567890-abcdef";
        String codeChallenge = pkceS256(codeVerifier);
        String state = "xyz789";

        // Step 1: unauthenticated /oauth2/authorize -> redirected to /login, session established
        URI authorizeUri = URI.create("/oauth2/authorize"
                + "?response_type=code&client_id=" + clientId
                + "&redirect_uri=" + REDIRECT_URI
                + "&scope=openid"
                + "&state=" + state
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256");

        ResponseEntity<Void> firstHop = noRedirectClient.getForEntity(authorizeUri, Void.class);
        assertThat(firstHop.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(firstHop.getHeaders().getLocation().toString()).contains("/login");
        String sessionCookie = firstHop.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(sessionCookie).isNotNull();

        // Step 2: POST /login — THIS is LoginOrchestrationService.login() actually
        // running behind an HTTP endpoint, not a unit test calling it directly.
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        loginHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        String loginBody = "username=alice&password=correct-horse-battery-staple&mfaCode=000000"
                + "&applicationId=" + applicationId;

        ResponseEntity<Void> loginResponse = noRedirectClient.postForEntity("/login",
                new HttpEntity<>(loginBody, loginHeaders), Void.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(loginResponse.getHeaders().getLocation().toString()).contains("/oauth2/authorize");
        String authenticatedCookie = firstNonNull(
                loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE), sessionCookie);

        // Step 3: replay /oauth2/authorize now authenticated -> redirected to our
        // redirect_uri with a real authorization code.
        HttpHeaders authedHeaders = new HttpHeaders();
        authedHeaders.add(HttpHeaders.COOKIE, authenticatedCookie);
        ResponseEntity<Void> secondHop = noRedirectClient.exchange(
                loginResponse.getHeaders().getLocation(), HttpMethod.GET, new HttpEntity<>(authedHeaders), Void.class);

        assertThat(secondHop.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        URI callback = secondHop.getHeaders().getLocation();
        assertThat(callback.toString()).startsWith(REDIRECT_URI);
        String code = extractQueryParam(callback, "code");
        assertThat(code).isNotBlank();
        assertThat(extractQueryParam(callback, "state")).isEqualTo(state);

        // Step 4: exchange the real authorization code for a real access token.
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String tokenBody = "grant_type=authorization_code"
                + "&code=" + code
                + "&redirect_uri=" + REDIRECT_URI
                + "&client_id=" + clientId
                + "&code_verifier=" + codeVerifier;

        ResponseEntity<String> tokenResponse = noRedirectClient.postForEntity("/oauth2/token",
                new HttpEntity<>(tokenBody, tokenHeaders), String.class);

        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tokenResponse.getBody()).contains("access_token");
        assertThat(tokenResponse.getBody()).contains("Bearer");
    }

    private static String pkceS256(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static String extractQueryParam(URI uri, String name) {
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv.length > 1 ? kv[1] : "";
            }
        }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /** Disables redirect-following so each hop's Location/Set-Cookie can be inspected. */
    private static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
