package com.texora.secops.sec.policy.service;

import com.texora.secops.sec.policy.domain.SecPolicyEvaluationLog;
import com.texora.secops.sec.policy.domain.SecPolicyVersion;
import com.texora.secops.sec.policy.dto.PolicyEvaluationRequest;
import com.texora.secops.sec.policy.dto.PolicyEvaluationResponse;
import com.texora.secops.sec.policy.repository.SecPolicyEvaluationLogRepository;
import com.texora.secops.sec.policy.repository.SecPolicyVersionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * THE most critical service in the Texora platform.
 *
 * <p>Implements default-deny policy evaluation per SEC-F-005. The five possible
 * reason codes and their resulting decisions:</p>
 * <pre>
 *   EXPLICIT_ALLOW      → ALLOW   (exactly one ALLOW applies, no DENY)
 *   EXPLICIT_DENY       → DENY    (any applicable DENY — DENY always wins)
 *   NO_MATCHING_POLICY  → DENY    (no policy matched)
 *   POLICY_CONFLICT     → DENY    (applicable policies conflict)
 *   EVALUATION_ERROR    → DENY    (anything threw during evaluation)
 * </pre>
 *
 * <p><strong>CRITICAL:</strong> The entire evaluation is wrapped in a try/catch whose
 * catch block returns DENY/EVALUATION_ERROR. An unhandled exception that the caller
 * interprets as success is the most dangerous bug possible in this platform.</p>
 */
@Service
public class PolicyEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyEvaluationService.class);

    // Decision constants
    public static final String DECISION_ALLOW = "ALLOW";
    public static final String DECISION_DENY  = "DENY";

    // Reason constants — used in logs, responses, and metrics
    public static final String REASON_EXPLICIT_ALLOW     = "EXPLICIT_ALLOW";
    public static final String REASON_EXPLICIT_DENY      = "EXPLICIT_DENY";
    public static final String REASON_NO_MATCHING_POLICY = "NO_MATCHING_POLICY";
    public static final String REASON_POLICY_CONFLICT    = "POLICY_CONFLICT";
    public static final String REASON_EVALUATION_ERROR   = "EVALUATION_ERROR";

    private final SecPolicyVersionRepository policyVersionRepository;
    private final SecPolicyEvaluationLogRepository evaluationLogRepository;
    private final MeterRegistry meterRegistry;

    public PolicyEvaluationService(
            SecPolicyVersionRepository policyVersionRepository,
            SecPolicyEvaluationLogRepository evaluationLogRepository,
            MeterRegistry meterRegistry) {
        this.policyVersionRepository = policyVersionRepository;
        this.evaluationLogRepository = evaluationLogRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Evaluates a policy decision for the given subject/resource/action tuple.
     *
     * <p>This method MUST NEVER throw — all exceptions are caught and produce DENY.
     * The caller must not interpret any exception path as success.</p>
     *
     * @param tenantId  the tenant context (resolved from the mTLS client cert)
     * @param request   the evaluation request from the calling service
     * @return a response with decision=ALLOW or decision=DENY and the reason
     */
    @Transactional
    public PolicyEvaluationResponse evaluate(UUID tenantId, PolicyEvaluationRequest request) {
        long startNano = System.nanoTime();

        // -----------------------------------------------------------------------
        // OUTER SAFETY NET: the entire evaluation is wrapped in try/catch.
        // If anything throws here, we return DENY/EVALUATION_ERROR.
        // This is not optional — it is the primary default-deny guarantee.
        // -----------------------------------------------------------------------
        try {
            return doEvaluate(tenantId, request, startNano);
        } catch (Exception ex) {
            // Any exception during evaluation must produce DENY, not an exception
            // that the caller could interpret as success (e.g. a null response).
            LOGGER.error(
                "EVALUATION_ERROR for caller={} resource={} action={} — returning DENY",
                request.getCallerService(), request.getResource(), request.getAction(), ex);

            int latencyMs = computeLatencyMs(startNano);
            PolicyEvaluationResponse denyResponse =
                    PolicyEvaluationResponse.deny(REASON_EVALUATION_ERROR);

            recordDecision(tenantId, request, denyResponse, null, latencyMs);
            incrementDenyMetric(request.getCallerService(), REASON_EVALUATION_ERROR);

            return denyResponse;
        }
    }

    // -------------------------------------------------------------------------
    // Internal evaluation logic — called from within the outer try/catch
    // -------------------------------------------------------------------------
    private PolicyEvaluationResponse doEvaluate(UUID tenantId,
                                                 PolicyEvaluationRequest request,
                                                 long startNano) {
        String resource = request.getResource();
        String action   = request.getAction();
        String caller   = request.getCallerService();

        LOGGER.debug("Evaluating policy: caller={} resource={} action={}", caller, resource, action);

        List<SecPolicyVersion> activeVersions =
                policyVersionRepository.findActiveVersionsForEvaluation(tenantId, resource, action);

        int latencyMs = computeLatencyMs(startNano);

        PolicyEvaluationResponse response = determineDecision(activeVersions);

        UUID matchedVersionId = response.getPolicyVersionId();
        recordDecision(tenantId, request, response, matchedVersionId, latencyMs);

        if (DECISION_DENY.equals(response.getDecision())) {
            incrementDenyMetric(caller, response.getReason());
            LOGGER.info("DENY caller={} resource={} action={} reason={} latency={}ms",
                    caller, resource, action, response.getReason(), latencyMs);
        } else {
            LOGGER.debug("ALLOW caller={} resource={} action={} latency={}ms",
                    caller, resource, action, latencyMs);
        }

        return response;
    }

    /**
     * Core decision logic — pure function, no I/O, testable in isolation.
     *
     * <pre>
     * Rules (in order):
     *   1. No active versions matched → DENY/NO_MATCHING_POLICY
     *   2. Any version has effect=DENY → DENY/EXPLICIT_DENY  (DENY always wins)
     *   3. More than one ALLOW applies → DENY/POLICY_CONFLICT
     *   4. Exactly one ALLOW applies → ALLOW/EXPLICIT_ALLOW
     * </pre>
     */
    PolicyEvaluationResponse determineDecision(List<SecPolicyVersion> activeVersions) {
        if (activeVersions == null || activeVersions.isEmpty()) {
            return PolicyEvaluationResponse.deny(REASON_NO_MATCHING_POLICY);
        }

        boolean hasExplicitDeny = activeVersions.stream()
                .anyMatch(v -> SecPolicyVersion.EFFECT_DENY.equals(v.getEffect()));

        if (hasExplicitDeny) {
            // DENY always wins — even if an ALLOW also applies
            return PolicyEvaluationResponse.deny(REASON_EXPLICIT_DENY);
        }

        List<SecPolicyVersion> allowVersions = activeVersions.stream()
                .filter(v -> SecPolicyVersion.EFFECT_ALLOW.equals(v.getEffect()))
                .toList();

        if (allowVersions.isEmpty()) {
            // No DENY, no ALLOW — shouldn't happen after the guard above, but be safe
            return PolicyEvaluationResponse.deny(REASON_NO_MATCHING_POLICY);
        }

        if (allowVersions.size() > 1) {
            // Multiple ALLOW policies match — conflict → DENY
            LOGGER.warn("POLICY_CONFLICT: {} ALLOW versions matched for resource evaluation",
                    allowVersions.size());
            return PolicyEvaluationResponse.deny(REASON_POLICY_CONFLICT);
        }

        // Exactly one unambiguous ALLOW, no DENY — this is the only path to ALLOW
        return PolicyEvaluationResponse.allow(allowVersions.get(0).getId());
    }

    // -------------------------------------------------------------------------
    // Logging and metrics — every decision is recorded (B.6 §4)
    // -------------------------------------------------------------------------

    private void recordDecision(UUID tenantId, PolicyEvaluationRequest request,
                                 PolicyEvaluationResponse response,
                                 UUID policyVersionId, int latencyMs) {
        try {
            PolicyEvaluationRequest.SubjectDto subject = request.getSubject();
            String subjectType = subject != null ? subject.getType() : null;
            String subjectId   = subject != null ? subject.getId()   : null;

            SecPolicyEvaluationLog log = new SecPolicyEvaluationLog(
                    UUID.randomUUID(),
                    tenantId,
                    policyVersionId,
                    request.getCallerService(),
                    subjectType,
                    subjectId,
                    request.getResource(),
                    request.getAction(),
                    response.getDecision(),
                    response.getReason(),
                    latencyMs,
                    request.getContext()
            );
            evaluationLogRepository.save(log);
        } catch (Exception ex) {
            // Log-write failure must not promote to an error that bypasses the decision.
            // The decision has already been determined; we just lose the log entry.
            LOGGER.error("Failed to persist evaluation log entry — decision stands: {}",
                    response.getDecision(), ex);
        }
    }

    private void incrementDenyMetric(String callerService, String reason) {
        try {
            Counter.builder("sec.policy.evaluation.deny")
                    .tag("caller", callerService)
                    .tag("reason", reason)
                    .description("Number of DENY decisions by caller and reason")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception ex) {
            LOGGER.warn("Failed to increment deny metric — non-critical", ex);
        }
    }

    private int computeLatencyMs(long startNano) {
        return (int) ((System.nanoTime() - startNano) / 1_000_000L);
    }
}
