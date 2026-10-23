package com.texora.qap.execution.adapter;

import java.util.UUID;

/**
 * THE ONLY class permitted to make test HTTP calls against the systems under test
 * (Shared Standards B.4 / QAP Requirement 1). No other class in the service may
 * issue direct HTTP requests as part of test execution.
 *
 * <p>This adapter is active in Part A. The implementation uses Spring WebClient
 * to execute API-level test assertions.
 */
public interface ApiTestRunnerAdapter {

    /**
     * Execute an API test case.
     *
     * @param testCaseId  the test case being executed
     * @param steps       JSON steps describing the HTTP requests to make and assertions to check
     * @param credential  resolved credential for authenticating as a test user
     * @return            execution result with outcome, captured payloads, and trace ID
     */
    ApiTestResult execute(UUID testCaseId, String steps, ResolvedTestCredential credential);

    // -------------------------------------------------------------------------

    record ApiTestResult(
            boolean passed,
            String traceId,            // OpenTelemetry trace ID from the response headers
            long durationMs,
            String redactedRequest,    // pre-redacted JSON; safe to persist
            String redactedResponse,   // pre-redacted JSON; safe to persist
            int httpStatus,
            String errorMessage
    ) {}

    record ResolvedTestCredential(
            String bearerToken  // held in-memory only; never persisted or logged
    ) {}
}
