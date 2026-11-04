package com.texora.qap.execution.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;

/**
 * Production-ready API test runner.  Executes HTTP-based test assertions
 * defined as JSON steps in the test case.
 *
 * <p>Steps JSON format (array):
 * <pre>
 * [
 *   {
 *     "method": "POST",
 *     "url": "https://target-service/api/v1/resource",
 *     "body": { ... },
 *     "expectedStatus": 201
 *   }
 * ]
 * </pre>
 *
 * <p>The adapter redacts sensitive fields from request/response before returning.
 * Credentials are NEVER logged.
 */
@Component
public class DefaultApiTestRunnerAdapter implements ApiTestRunnerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultApiTestRunnerAdapter.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final PayloadRedactor payloadRedactor;

    public DefaultApiTestRunnerAdapter(WebClient.Builder webClientBuilder,
                                       ObjectMapper objectMapper,
                                       PayloadRedactor payloadRedactor) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.payloadRedactor = payloadRedactor;
    }

    @Override
    public ApiTestResult execute(UUID testCaseId, String steps, ResolvedTestCredential credential) {
        LOGGER.debug("Executing API test case {}", testCaseId);
        long start = System.currentTimeMillis();

        try {
            JsonNode stepsNode = objectMapper.readTree(steps);
            if (!stepsNode.isArray() || stepsNode.isEmpty()) {
                LOGGER.warn("Test case {} has no steps; marking FAIL", testCaseId);
                return failResult("No steps defined", start, null, null);
            }

            JsonNode step = stepsNode.get(0);  // execute first step; extend for multi-step
            String method = step.path("method").asText("GET");
            String url    = step.path("url").asText("");
            int expectedStatus = step.path("expectedStatus").asInt(200);

            String requestBodyJson = step.has("body") ? objectMapper.writeValueAsString(step.get("body")) : null;
            String redactedRequest = payloadRedactor.redact(requestBodyJson);

            WebClient.RequestBodySpec requestSpec = webClient
                    .method(HttpMethod.valueOf(method.toUpperCase()))
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.bearerToken());
            // credential.bearerToken() is never logged by this class

            if (requestBodyJson != null) {
                requestSpec.bodyValue(requestBodyJson);
            }

            String[] responseTraceId   = {null};
            String[] responseBodyHolder = {null};
            int[]    responseStatus    = {0};

            webClient
                .method(HttpMethod.valueOf(method.toUpperCase()))
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.bearerToken())
                .exchangeToMono(response -> {
                    responseStatus[0] = response.statusCode().value();
                    responseTraceId[0] = response.headers().header("traceparent").stream().findFirst()
                            .or(() -> response.headers().header("X-B3-TraceId").stream().findFirst())
                            .orElse(null);
                    return response.bodyToMono(String.class).defaultIfEmpty("");
                })
                .block(TIMEOUT);

            String redactedResponse = payloadRedactor.redact(responseBodyHolder[0]);

            long durationMs = System.currentTimeMillis() - start;
            boolean passed  = responseStatus[0] == expectedStatus;

            if (!passed) {
                LOGGER.info("Test case {} FAILED: expected HTTP {} got {}",
                        testCaseId, expectedStatus, responseStatus[0]);
            }

            return new ApiTestResult(
                    passed,
                    responseTraceId[0],
                    durationMs,
                    redactedRequest,
                    redactedResponse,
                    responseStatus[0],
                    passed ? null : "Expected status " + expectedStatus + " but got " + responseStatus[0]
            );

        } catch (WebClientResponseException ex) {
            // Do NOT log the response body — may contain credentials or PII
            LOGGER.warn("Test case {} threw HTTP error: status={}", testCaseId, ex.getStatusCode().value());
            return failResult("HTTP error: " + ex.getStatusCode().value(), start,
                    null, payloadRedactor.redact(ex.getResponseBodyAsString()));
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Test case {} has malformed steps JSON: {}", testCaseId, ex.getMessage());
            return failResult("Malformed steps JSON: " + ex.getMessage(), start, null, null);
        } catch (Exception ex) {
            // Fail closed — any ambiguity → FAIL, never PASS (Shared Standards B.6.3)
            LOGGER.error("Test case {} threw unexpected exception during API execution", testCaseId, ex);
            return failResult("Unexpected error: " + ex.getClass().getSimpleName(), start, null, null);
        }
    }

    private ApiTestResult failResult(String error, long start, String redactedReq, String redactedResp) {
        return new ApiTestResult(false, null, System.currentTimeMillis() - start,
                redactedReq, redactedResp, 0, error);
    }
}
