package com.texora.qap.correlation.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Resolves a failing request's OpenTelemetry trace_id against the SIEM's
 * normalized_event corpus (SIEM LLD §4.3) or the OpenTelemetry backend directly,
 * to retrieve the real backend service, endpoint, exception, and HTTP status code
 * that caused the test failure.
 *
 * <p>This is the mechanism that closes the "a failed test just says failed" gap:
 * instead of a red X in CI, QAP produces a ticket that names exactly which service
 * and which endpoint caused the problem.
 *
 * <p>Communication to the observability backend goes via this class only.
 * No other class may query the SIEM or OTel backend for trace data.
 */
@Component
public class ObservabilityCorrelationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityCorrelationClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final String siemQueryUrl;

    public ObservabilityCorrelationClient(WebClient.Builder webClientBuilder,
                                          @Value("${qap.siem.query-url:http://siem-service/api/v1/events/search}") String siemQueryUrl) {
        this.webClient = webClientBuilder
                .baseUrl(siemQueryUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.siemQueryUrl = siemQueryUrl;
    }

    /**
     * Look up the real backend error for a given trace ID.
     *
     * @param traceId OpenTelemetry trace ID captured during the failing request
     * @return correlation result, or a "not-found" result if the trace cannot be resolved
     */
    public CorrelationResult resolve(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            LOGGER.debug("No trace ID provided; returning not-correlated result");
            return CorrelationResult.notCorrelated();
        }

        try {
            LOGGER.debug("Resolving trace_id={} against SIEM at {}", traceId, siemQueryUrl);

            // Query SIEM normalized event corpus for this trace
            SiemQueryResponse response = webClient.post()
                    .bodyValue(Map.of("traceId", traceId))
                    .retrieve()
                    .bodyToMono(SiemQueryResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || response.events() == null || response.events().isEmpty()) {
                LOGGER.info("No SIEM events found for trace_id={}", traceId);
                return CorrelationResult.notCorrelated();
            }

            // Take the highest-severity (typically 5xx) event
            SiemEvent worst = response.events().stream()
                    .filter(e -> e.httpStatus() != null && e.httpStatus() >= 500)
                    .findFirst()
                    .orElse(response.events().get(0));

            LOGGER.info("Correlated trace_id={} to service={} endpoint={} status={}",
                    traceId, worst.serviceId(), worst.endpoint(), worst.httpStatus());

            return new CorrelationResult(
                    true,
                    worst.serviceId(),
                    worst.endpoint(),
                    worst.httpStatus(),
                    worst.exceptionMessage()
            );

        } catch (Exception ex) {
            // Fail safe — correlation failure does not block defect filing; it just
            // means the ticket will have less detail.  Never propagate to caller as exception.
            LOGGER.warn("ObservabilityCorrelationClient: failed to resolve trace_id={}: {}",
                    traceId, ex.getMessage());
            return CorrelationResult.notCorrelated();
        }
    }

    // -------------------------------------------------------------------------

    public record CorrelationResult(
            boolean correlated,
            String sourceService,
            String endpoint,
            Integer httpStatus,
            String exception
    ) {
        public static CorrelationResult notCorrelated() {
            return new CorrelationResult(false, null, null, null, null);
        }
    }

    /** Mirrors the SIEM event-search API response shape (SIEM LLD §6.1). */
    record SiemQueryResponse(java.util.List<SiemEvent> events) {}

    record SiemEvent(
            String serviceId,
            String endpoint,
            Integer httpStatus,
            String exceptionMessage
    ) {}
}
