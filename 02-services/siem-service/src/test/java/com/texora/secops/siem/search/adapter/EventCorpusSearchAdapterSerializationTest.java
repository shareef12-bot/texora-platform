package com.texora.secops.siem.search.adapter;

import com.sun.net.httpserver.HttpServer;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.search.client.EventCorpusClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the adapter can write and read NormalizedEvent, including its Instant fields. */
class EventCorpusSearchAdapterSerializationTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> searchResponse = new AtomicReference<>("{}");
    private EventCorpusSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] req = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(req, StandardCharsets.UTF_8));
            String path = exchange.getRequestURI().getPath();
            byte[] resp = (path.endsWith("/_search") ? searchResponse.get() : "{\"result\":\"created\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        adapter = new EventCorpusSearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(),
                "normalized_event", "", "", 2000, 5000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private NormalizedEvent sampleEvent() {
        NormalizedEvent e = new NormalizedEvent();
        e.setId("evt-1");
        e.setSourceService("sso");
        e.setOriginalEventId("orig-1");
        e.setEventType("login.attempt");
        e.setOutcome("SUCCESS");
        e.setTenantScope(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        e.setOccurredAt(Instant.parse("2026-11-07T07:39:37.462Z"));
        e.setIngestedAt(Instant.parse("2026-11-11T07:23:22Z"));
        return e;
    }

    @Test
    void indexWritesInstantFieldsAsIsoStrings() throws Exception {
        adapter.index(sampleEvent());
        assertThat(lastBody.get()).contains("\"occurredAt\":\"2026-11-07T07:39:37.462Z\"");
        assertThat(lastBody.get()).contains("\"ingestedAt\":\"2026-11-11T07:23:22Z\"");
    }

    @Test
    void searchReadsInstantFieldsBack() throws Exception {
        searchResponse.set("{\"hits\":{\"total\":{\"value\":1},\"hits\":[{\"_source\":"
                + "{\"id\":\"evt-1\",\"occurredAt\":\"2026-11-07T07:39:37.462Z\","
                + "\"ingestedAt\":\"2026-11-11T07:23:22Z\","
                + "\"tenantScope\":\"00000000-0000-0000-0000-000000000001\"}}]}}");
        EventCorpusClient.EventCorpusSearchResult result = adapter.search(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), null, null, null, 0, 10);
        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getEvents().get(0).getOccurredAt()).isEqualTo(Instant.parse("2026-11-07T07:39:37.462Z"));
    }
}
