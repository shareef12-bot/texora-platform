package com.texora.qap.execution.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * LOCAL STUB — NOT PRODUCTION CODE.
 *
 * <p>This implementation of {@link SeleniumGridAdapter} is active for Part A of the QAP
 * build (and whenever the "selenium-stub" Spring profile is active). It does NOT connect
 * to a real Selenium Grid. Every call logs a loud warning and returns a failed result so
 * that the rest of the failure-handling pipeline (trace correlation, defect filing, flaky
 * detection) can be exercised without real browser infrastructure.
 *
 * <p>A real implementation will replace this class in Part B, once 2–3 product UIs are
 * stable. The {@link SeleniumGridAdapter} interface MUST NOT change when that happens.
 *
 * <p><strong>DO NOT promote this class to production use. It is a test stub only.</strong>
 */
@Component
@Profile({"default", "selenium-stub", "test"})
public class StubSeleniumGridAdapter implements SeleniumGridAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubSeleniumGridAdapter.class);

    private static final String STUB_SESSION_ID = "STUB-SESSION-NOT-REAL";

    /**
     * {@inheritDoc}
     *
     * <p><strong>STUB:</strong> Always returns a failed result. Real browser execution
     * is deferred to Part B.
     */
    @Override
    public SeleniumExecutionResult execute(UUID testCaseId, String steps, ResolvedCredential credential) {
        LOGGER.warn(
            "⚠️  STUB SeleniumGridAdapter: execute() called for testCaseId={} — " +
            "NO real Selenium Grid is connected. This stub is a Part A placeholder. " +
            "Part B will replace this with a real adapter once product UIs are stable.",
            testCaseId
        );
        return new SeleniumExecutionResult(
            false,
            STUB_SESSION_ID,
            null,
            0L,
            "SeleniumGridAdapter is a stub in Part A — Selenium UI tests not yet active"
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>STUB:</strong> Returns a placeholder path. No screenshot is captured.
     */
    @Override
    public String captureScreenshot(String sessionId) {
        LOGGER.warn(
            "⚠️  STUB SeleniumGridAdapter: captureScreenshot() called for sessionId={} — " +
            "no real screenshot available in Part A.",
            sessionId
        );
        return "stub/no-screenshot-available.png";
    }
}
