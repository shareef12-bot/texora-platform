package com.texora.secops.cgi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PolicyDecisionClientTest {

    private MockRestServiceServer server;
    private PolicyDecisionClient client;
    private final CallerContext caller = new CallerContext(UUID.randomUUID(), UUID.randomUUID(), Set.of("cgi.route.read"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://sec.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PolicyDecisionClient(builder.build());
    }

    @Test
    void explicitAllowIsTheOnlyWayToGetAllow() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.action").value("cgi.route.invoke"))
                .andExpect(jsonPath("$.resource").value("/legacy/report"))
                .andRespond(withSuccess("{\"decision\":\"ALLOW\"}", MediaType.APPLICATION_JSON));
        PolicyDecision d = client.evaluate(caller, "cgi.route.invoke", "/legacy/report");
        assertThat(d.isAllowed()).isTrue();
        server.verify();
    }

    @Test
    void requestNeverCarriesParametersOrTokens() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))))
                .andRespond(withSuccess("{\"decision\":\"ALLOW\"}", MediaType.APPLICATION_JSON));
        client.evaluate(caller, "cgi.route.invoke", "/legacy/report");
        server.verify();
    }

    @Test
    void denyAndUnknownDecisionsAreDeny() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate"))
                .andRespond(withSuccess("{\"decision\":\"DENY\"}", MediaType.APPLICATION_JSON));
        assertThat(client.evaluate(caller, "a", "r").isAllowed()).isFalse();
        server.reset();
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate"))
                .andRespond(withSuccess("{\"decision\":\"MAYBE\"}", MediaType.APPLICATION_JSON));
        assertThat(client.evaluate(caller, "a", "r").isAllowed()).isFalse();
    }

    @Test
    void malformedOrEmptyResponsesAreDeny() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate"))
                .andRespond(withSuccess("{\"allowed\":true}", MediaType.APPLICATION_JSON));
        PolicyDecision d = client.evaluate(caller, "a", "r");
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.getReasonCode()).isEqualTo(PolicyDecision.REASON_SEC_MALFORMED);
        server.reset();
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate")).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThat(client.evaluate(caller, "a", "r").isAllowed()).isFalse();
    }

    @Test
    void serverErrorsAreDeny() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(client.evaluate(caller, "a", "r").isAllowed()).isFalse();
        server.reset();
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(client.evaluate(caller, "a", "r").isAllowed()).isFalse();
    }

    @Test
    void timeoutIsDenyFlaggedAsTimeout() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate")).andRespond(request -> {
            throw new HttpTimeoutException("timed out");
        });
        PolicyDecision d = client.evaluate(caller, "a", "r");
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.isTimeout()).isTrue();
    }

    @Test
    void connectionFailureIsDenyNotTimeout() {
        server.expect(requestTo("http://sec.test/api/v1/policies/evaluate")).andRespond(request -> {
            throw new ConnectException("refused");
        });
        PolicyDecision d = client.evaluate(caller, "a", "r");
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.isTimeout()).isFalse();
    }
}
