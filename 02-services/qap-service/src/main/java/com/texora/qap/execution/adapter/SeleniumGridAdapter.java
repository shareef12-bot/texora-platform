package com.texora.qap.execution.adapter;

import java.util.UUID;

/**
 * THE ONLY class permitted to talk to the Selenium Grid (Shared Standards B.4 /
 * QAP Requirement 1). No other class in this service may import Selenium or
 * WebDriver libraries.
 *
 * <p><strong>Part A note:</strong> This interface exists for isolation purposes.
 * The only live implementation for Part A is {@link StubSeleniumGridAdapter} —
 * a loud stub that logs a warning and returns a failed result. A real
 * implementation is deferred to Part B, once 2–3 product UIs are stable
 * enough to drive with Selenium.
 *
 * <p>The interface must NOT change when the real implementation is added;
 * only the adapter class changes.
 */
public interface SeleniumGridAdapter {

    /**
     * Execute a UI test case on the Selenium Grid.
     *
     * @param testCaseId  the test case to execute
     * @param steps       serialised JSON steps (from qap_test_case.steps)
     * @param credential  resolved credential material (held in-memory, never logged)
     * @return            execution result
     */
    SeleniumExecutionResult execute(UUID testCaseId, String steps, ResolvedCredential credential);

    /**
     * Capture a screenshot from the last page state for the given session.
     * Returns an object-storage reference path.
     *
     * @param sessionId the WebDriver session ID
     * @return object-storage path to the stored screenshot
     */
    String captureScreenshot(String sessionId);

    // -------------------------------------------------------------------------

    record SeleniumExecutionResult(
            boolean passed,
            String sessionId,
            String traceId,
            long durationMs,
            String errorMessage
    ) {}

    record ResolvedCredential(
            String username,
            String password   // held in-memory only; never persisted or logged
    ) {}
}
