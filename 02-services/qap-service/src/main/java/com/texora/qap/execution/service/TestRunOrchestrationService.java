package com.texora.qap.execution.service;

import com.texora.qap.correlation.client.ObservabilityCorrelationClient;
import com.texora.qap.credentials.CredentialReferenceService;
import com.texora.qap.defect.service.DefectFilingService;
import com.texora.qap.domain.QapTestCase;
import com.texora.qap.domain.QapTestResult;
import com.texora.qap.domain.QapTestRun;
import com.texora.qap.execution.adapter.ApiTestRunnerAdapter;
import com.texora.qap.execution.adapter.SeleniumGridAdapter;
import com.texora.qap.flaky.FlakyTestDetectionService;
import com.texora.qap.repository.QapTestCaseRepository;
import com.texora.qap.repository.QapTestResultRepository;
import com.texora.qap.repository.QapTestRunRepository;
import com.texora.qap.suite.service.TestSuiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives a test suite through execution and implements the full
 * failure → trace → deduplicated-defect pipeline (QAP Requirement 2).
 *
 * <p>Fail-closed: if the runner crashes or the result is uncertain,
 * the run is marked FAILED or INCONCLUSIVE.  It is NEVER marked PASSED.
 */
@Service
public class TestRunOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRunOrchestrationService.class);

    private final QapTestRunRepository      testRunRepository;
    private final QapTestResultRepository   testResultRepository;
    private final QapTestCaseRepository     testCaseRepository;
    private final SeleniumGridAdapter       seleniumGridAdapter;
    private final ApiTestRunnerAdapter      apiTestRunnerAdapter;
    private final ObservabilityCorrelationClient correlationClient;
    private final DefectFilingService       defectFilingService;
    private final FlakyTestDetectionService flakyTestDetectionService;
    private final CredentialReferenceService credentialReferenceService;
    private final TestSuiteService          testSuiteService;

    public TestRunOrchestrationService(
            QapTestRunRepository testRunRepository,
            QapTestResultRepository testResultRepository,
            QapTestCaseRepository testCaseRepository,
            SeleniumGridAdapter seleniumGridAdapter,
            ApiTestRunnerAdapter apiTestRunnerAdapter,
            ObservabilityCorrelationClient correlationClient,
            DefectFilingService defectFilingService,
            FlakyTestDetectionService flakyTestDetectionService,
            CredentialReferenceService credentialReferenceService,
            TestSuiteService testSuiteService) {
        this.testRunRepository        = testRunRepository;
        this.testResultRepository     = testResultRepository;
        this.testCaseRepository       = testCaseRepository;
        this.seleniumGridAdapter      = seleniumGridAdapter;
        this.apiTestRunnerAdapter     = apiTestRunnerAdapter;
        this.correlationClient        = correlationClient;
        this.defectFilingService      = defectFilingService;
        this.flakyTestDetectionService= flakyTestDetectionService;
        this.credentialReferenceService = credentialReferenceService;
        this.testSuiteService         = testSuiteService;
    }

    /**
     * Trigger a new test run for a suite.
     *
     * @param suiteId      the suite to run
     * @param tenantId     the tenant context (from SSO token)
     * @param triggeredBy  "CI" | "SCHEDULE" | "MANUAL"
     * @return             the created (not yet completed) test run
     */
    @Transactional
    public QapTestRun triggerRun(UUID suiteId, UUID tenantId, String triggeredBy) {
        // Validate suite exists for this tenant (cross-tenant → 403 from RBAC, not here)
        testSuiteService.getSuite(suiteId, tenantId);

        QapTestRun run = new QapTestRun(
                UUID.randomUUID(), suiteId, tenantId, triggeredBy, Instant.now(), "RUNNING");

        run = testRunRepository.save(run);
        LOGGER.info("Test run {} started: suite={} triggeredBy={}", run.getId(), suiteId, triggeredBy);

        // Execute asynchronously in the same transaction — for production, consider
        // @Async or a job queue to avoid blocking the HTTP response.
        // For Part A, synchronous execution is acceptable.
        executeRun(run);

        return testRunRepository.findById(run.getId()).orElse(run);
    }

    /**
     * Execute all test cases in a run and record results.
     * Fail-closed: any unhandled exception marks the run FAILED.
     */
    private void executeRun(QapTestRun run) {
        UUID suiteId  = run.getSuiteId();
        UUID tenantId = run.getTenantId();

        List<QapTestCase> cases = testCaseRepository.findAllBySuiteId(suiteId);

        // Resolve the suite type to decide which runner to use
        String suiteType = testSuiteService.findSuite(suiteId, tenantId)
                .map(s -> s.getSuiteType())
                .orElse("API");

        try {
            // Resolve test-environment credential once per run (held in-memory only)
            CredentialReferenceService.ResolvedCredential credential = null;
            try {
                credential = credentialReferenceService.resolve(tenantId, suiteId.toString());
            } catch (CredentialReferenceService.CredentialNotFoundException ex) {
                LOGGER.warn("No credential reference for suite {}; proceeding without auth", suiteId);
            }

            int failCount = 0;
            for (QapTestCase tc : cases) {
                QapTestResult result = executeTestCase(tc, run, suiteType, credential);
                testResultRepository.save(result);

                if ("FAIL".equals(result.getOutcome()) || "INCONCLUSIVE".equals(result.getOutcome())) {
                    failCount++;
                    // Trigger defect filing for failures
                    if ("FAIL".equals(result.getOutcome())) {
                        try {
                            defectFilingService.processFailure(result, tenantId);
                        } catch (Exception ex) {
                            // Defect-filing failure must not abort the rest of the run
                            LOGGER.error("Defect filing failed for result {}: {}", result.getId(), ex.getMessage(), ex);
                        }
                    }
                }
            }

            String finalStatus = cases.isEmpty() ? "COMPLETED"
                    : (failCount == 0 ? "COMPLETED" : "FAILED");

            finaliseRun(run, finalStatus);

        } catch (Exception ex) {
            // Fail closed — any crash → FAILED, never PASSED
            LOGGER.error("Test run {} crashed during execution", run.getId(), ex);
            finaliseRun(run, "FAILED");
        }
    }

    /**
     * Execute a single test case and return the result.
     * Catch-all ensures a crash → INCONCLUSIVE, never PASS.
     */
    private QapTestResult executeTestCase(QapTestCase tc, QapTestRun run,
                                          String suiteType,
                                          CredentialReferenceService.ResolvedCredential credential) {
        try {
            if ("UI".equals(suiteType)) {
                return executeUiCase(tc, run, credential);
            } else {
                return executeApiCase(tc, run, credential);
            }
        } catch (Exception ex) {
            // Fail closed — unexpected crash → INCONCLUSIVE (B.6.3)
            LOGGER.error("Unexpected error executing test case {}", tc.getId(), ex);
            QapTestResult result = new QapTestResult(
                    UUID.randomUUID(), run.getId(), tc.getId(),
                    "INCONCLUSIVE", 0L, Instant.now());
            return result;
        }
    }

    private QapTestResult executeApiCase(QapTestCase tc, QapTestRun run,
                                          CredentialReferenceService.ResolvedCredential cred) {
        ApiTestRunnerAdapter.ResolvedTestCredential adapterCred = cred != null
                ? new ApiTestRunnerAdapter.ResolvedTestCredential(cred.password())
                : new ApiTestRunnerAdapter.ResolvedTestCredential("");

        ApiTestRunnerAdapter.ApiTestResult raw = apiTestRunnerAdapter.execute(
                tc.getId(), tc.getSteps(), adapterCred);

        String outcome = raw.passed() ? "PASS" : "FAIL";
        QapTestResult result = new QapTestResult(
                UUID.randomUUID(), run.getId(), tc.getId(),
                outcome, raw.durationMs(), Instant.now());

        result.setRedactedRequest(raw.redactedRequest());
        result.setRedactedResponse(raw.redactedResponse());
        result.setTraceId(raw.traceId());

        // Update flaky tracking for passes too
        flakyTestDetectionService.recordOutcome(tc.getId(), run.getTenantId(), raw.passed());

        if (!raw.passed() && raw.traceId() != null) {
            // Correlation: resolve trace to real backend error
            ObservabilityCorrelationClient.CorrelationResult correlation =
                    correlationClient.resolve(raw.traceId());
            if (correlation.correlated()) {
                result.setCorrelatedService(correlation.sourceService());
                result.setCorrelatedEndpoint(correlation.endpoint());
                result.setCorrelatedHttpStatus(correlation.httpStatus());
                result.setCorrelatedExceptionType(correlation.exception());
            }
        }

        return result;
    }

    private QapTestResult executeUiCase(QapTestCase tc, QapTestRun run,
                                         CredentialReferenceService.ResolvedCredential cred) {
        SeleniumGridAdapter.ResolvedCredential seleniumCred = cred != null
                ? new SeleniumGridAdapter.ResolvedCredential(cred.username(), cred.password())
                : new SeleniumGridAdapter.ResolvedCredential("", "");

        SeleniumGridAdapter.SeleniumExecutionResult raw = seleniumGridAdapter.execute(
                tc.getId(), tc.getSteps(), seleniumCred);

        String outcome = raw.passed() ? "PASS" : "FAIL";
        QapTestResult result = new QapTestResult(
                UUID.randomUUID(), run.getId(), tc.getId(),
                outcome, raw.durationMs(), Instant.now());

        result.setTraceId(raw.traceId());
        flakyTestDetectionService.recordOutcome(tc.getId(), run.getTenantId(), raw.passed());

        if (!raw.passed()) {
            // Capture screenshot ref
            String screenshotRef = seleniumGridAdapter.captureScreenshot(raw.sessionId());
            result.setScreenshotRef(screenshotRef);

            // Correlation
            if (raw.traceId() != null) {
                ObservabilityCorrelationClient.CorrelationResult correlation =
                        correlationClient.resolve(raw.traceId());
                if (correlation.correlated()) {
                    result.setCorrelatedService(correlation.sourceService());
                    result.setCorrelatedEndpoint(correlation.endpoint());
                    result.setCorrelatedHttpStatus(correlation.httpStatus());
                    result.setCorrelatedExceptionType(correlation.exception());
                }
            }
        }

        return result;
    }

    @Transactional
    private void finaliseRun(QapTestRun run, String status) {
        run.setStatus(status);
        run.setCompletedAt(Instant.now());
        testRunRepository.save(run);
        LOGGER.info("Test run {} finalised with status={}", run.getId(), status);
    }

    // ---- Query ----------------------------------------------------------

    public Optional<QapTestRun> findRun(UUID runId, UUID tenantId) {
        return testRunRepository.findByIdAndTenantId(runId, tenantId);
    }

    public List<QapTestResult> getResults(UUID runId, UUID tenantId) {
        // Verify run belongs to tenant
        testRunRepository.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new RunNotFoundException(runId, tenantId));
        return testResultRepository.findAllByTestRunId(runId);
    }

    public static class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(UUID runId, UUID tenantId) {
            super("Test run " + runId + " not found for tenant " + tenantId);
        }
    }
}
