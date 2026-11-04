package com.texora.secops.vpn.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.texora.secops.vpn.config.VpnProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a real local HTTP server. Only an exact {"decision":"ALLOW"} on a 200 may ever yield ALLOW. */
class PolicyDecisionClientTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body = "{\"decision\":\"ALLOW\"}";
    private volatile long delayMs = 0;
    private volatile String lastRequestBody;
    private final VpnProperties props = new VpnProperties();
    private PolicyDecisionClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/policies/decisions", ex -> {
            calls.incrementAndGet();
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        props.getSec().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getSec().setTimeoutMs(400);
        props.getSec().setRevalidationDeadlineMs(3000);
        client = new PolicyDecisionClient(HttpClient.newHttpClient(), props, new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SecDecisionRequest req() {
        return new SecDecisionRequest(UUID.randomUUID(), UUID.randomUUID(), "vpn.session.establish", "vpn:access-policy:x");
    }

    @Test
    void exactAllowIsAllow() {
        assertThat(client.decide(req(), 1)).isEqualTo(SecDecision.ALLOW);
        assertThat(lastRequestBody).contains("vpn.session.establish").contains("tenantId");
    }

    @Test
    void explicitDenyIsDenyAndIsNotRetried() {
        body = "{\"decision\":\"DENY\"}";
        assertThat(client.decide(req(), 3)).isEqualTo(SecDecision.DENY);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anythingElseIsUnavailableNeverAllow() {
        for (String b : new String[]{"{\"decision\":\"allow\"}", "{\"decision\":\"MAYBE\"}", "{\"decision\":true}",
                "{}", "not json", "", "[]", "{\"decision\":\"ALLOW \"}"}) {
            body = b;
            assertThat(client.decide(req(), 1)).as(b).isEqualTo(SecDecision.UNAVAILABLE);
        }
    }

    @Test
    void non200IsUnavailableEvenWithAllowBody() {
        for (int s : new int[]{201, 204, 301, 400, 401, 403, 404, 500, 503}) {
            status = s;
            body = "{\"decision\":\"ALLOW\"}";
            assertThat(client.decide(req(), 1)).as("status %d", s).isEqualTo(SecDecision.UNAVAILABLE);
        }
    }

    @Test
    void timeoutIsUnavailable() {
        delayMs = 1500;
        long start = System.nanoTime();
        assertThat(client.decide(req(), 1)).isEqualTo(SecDecision.UNAVAILABLE);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1200);
    }

    @Test
    void connectionRefusedIsUnavailable() {
        server.stop(0);
        assertThat(client.decide(req(), 2)).isEqualTo(SecDecision.UNAVAILABLE);
    }

    @Test
    void unreachableConfiguredUrlIsUnavailable() {
        props.getSec().setBaseUrl("http://127.0.0.1:1");
        assertThat(client.decide(req(), 1)).isEqualTo(SecDecision.UNAVAILABLE);
    }

    @Test
    void revalidationRetriesAbsorbAnIsolatedBlipButNotAnOutage() {
        // first call fails, second succeeds -> ALLOW
        AtomicInteger n = new AtomicInteger();
        server.removeContext("/api/v1/policies/decisions");
        server.createContext("/api/v1/policies/decisions", ex -> {
            byte[] out = "{\"decision\":\"ALLOW\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(n.incrementAndGet() == 1 ? 503 : 200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        assertThat(client.decide(req(), 2)).isEqualTo(SecDecision.ALLOW);
        assertThat(n.get()).isEqualTo(2);
        // sustained outage: all attempts fail -> UNAVAILABLE
        status = 503;
        server.removeContext("/api/v1/policies/decisions");
        server.createContext("/api/v1/policies/decisions", ex -> {
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        assertThat(client.decide(req(), 2)).isEqualTo(SecDecision.UNAVAILABLE);
    }

    @Test
    void oversizeBodyIsUnavailable() {
        body = "{\"decision\":\"ALLOW\",\"pad\":\"" + "x".repeat(70_000) + "\"}";
        assertThat(client.decide(req(), 1)).isEqualTo(SecDecision.UNAVAILABLE);
    }
}
