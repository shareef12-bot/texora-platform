package com.texora.secops.siem.search.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.search.client.EventCorpusClient;
import com.texora.secops.siem.search.client.EventCorpusUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * B.4 adapter for the two-store architecture's search side: the sole class in this service that
 * speaks the OpenSearch/Elasticsearch-compatible REST wire protocol. Deliberately implemented
 * against the plain HTTP document/query API (index + _search) rather than a version-pinned typed
 * client library, so this service can point at OpenSearch, an Elasticsearch-API-compatible
 * store, or a managed equivalent without a dependency bump - "OpenSearch-compatible store" per
 * the LLD is a wire-protocol commitment, not a specific product.
 */
@Component
public class EventCorpusSearchAdapter implements EventCorpusClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventCorpusSearchAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String indexName;
    private final String username;
    private final String password;
    private final Duration readTimeout;

    public EventCorpusSearchAdapter(@Value("${siem.search.backend.base-url}") String baseUrl,
                                     @Value("${siem.search.index-prefix}") String indexPrefix,
                                     @Value("${siem.search.backend.username:}") String username,
                                     @Value("${siem.search.backend.password:}") String password,
                                     @Value("${siem.search.backend.connect-timeout-ms:2000}") long connectTimeoutMs,
                                     @Value("${siem.search.backend.read-timeout-ms:5000}") long readTimeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.indexName = indexPrefix;
        this.username = username;
        this.password = password;
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public void index(NormalizedEvent event) throws EventCorpusUnavailableException {
        try {
            String body = MAPPER.writeValueAsString(event);
            HttpRequest request = requestBuilder(baseUrl + "/" + indexName + "/_doc/" + event.getId())
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new EventCorpusUnavailableException(
                        "Event corpus store returned HTTP " + response.statusCode() + " indexing event " + event.getId(), null);
            }
        } catch (EventCorpusUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EventCorpusUnavailableException("Failed to index event " + event.getId() + " into the event corpus", ex);
        }
    }

    @Override
    public EventCorpusSearchResult search(UUID tenantScope, String eventType, String sourceService,
                                           String productId, int page, int pageSize) throws EventCorpusUnavailableException {
        try {
            ObjectNode query = MAPPER.createObjectNode();
            query.put("from", page * pageSize);
            query.put("size", pageSize);
            ObjectNode boolNode = MAPPER.createObjectNode();
            ArrayNode filters = boolNode.putArray("filter");
            filters.add(termFilter("tenantScope", tenantScope.toString()));
            addIfPresent(filters, "eventType", eventType);
            addIfPresent(filters, "sourceService", sourceService);
            addIfPresent(filters, "productId", productId);
            query.set("query", MAPPER.createObjectNode().set("bool", boolNode));

            HttpRequest request = requestBuilder(baseUrl + "/" + indexName + "/_search")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(query), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new EventCorpusUnavailableException(
                        "Event corpus store returned HTTP " + response.statusCode() + " on search", null);
            }
            return parseSearchResponse(response.body());
        } catch (EventCorpusUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            // Includes connect timeout / refused / read timeout - all treated as "backend
            // impaired". TenantFilteredSearchService is responsible for the graceful-degradation
            // behaviour on top of this exception (Requirement 3 non-functional).
            throw new EventCorpusUnavailableException("Event corpus search backend unreachable", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(String uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(readTimeout);
        if (username != null && !username.isBlank()) {
            String creds = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + creds);
        }
        return builder;
    }

    private ObjectNode termFilter(String field, String value) {
        ObjectNode term = MAPPER.createObjectNode();
        term.put(field, value);
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.set("term", term);
        return wrapper;
    }

    private void addIfPresent(ArrayNode filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(termFilter(field, value));
        }
    }

    private EventCorpusSearchResult parseSearchResponse(String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        JsonNode hitsNode = root.path("hits");
        long total = hitsNode.path("total").path("value").asLong(0);
        List<NormalizedEvent> events = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            JsonNode source = hit.path("_source");
            events.add(MAPPER.treeToValue(source, NormalizedEvent.class));
        }
        return new EventCorpusSearchResult(events, total);
    }
}
