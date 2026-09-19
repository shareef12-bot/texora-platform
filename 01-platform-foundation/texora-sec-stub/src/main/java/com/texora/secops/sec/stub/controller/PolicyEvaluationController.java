package com.texora.secops.sec.stub.controller;

import com.texora.secops.sec.stub.dto.PolicyEvaluationRequest;
import com.texora.secops.sec.stub.dto.PolicyEvaluationResponse;
import com.texora.secops.sec.stub.dto.StubDecisionRule;
import com.texora.secops.sec.stub.config.StubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SEC API stub — implements {@code POST /api/v1/policies/evaluate} exactly as
 * specified in SEC LLD §6.2.
 *
 * <p><strong>Decision logic (in order):</strong>
 * <ol>
 *   <li>Validate the request (tenant, subject, resourceType, action required)</li>
 *   <li>Scan configured {@link StubDecisionRule}s in order — first match wins</li>
 *   <li>If no rule matches, apply the global default from {@link StubProperties}
 *       ({@code texora.sec.stub.default-decision}, default: PERMIT)</li>
 *   <li>Log every evaluation at INFO level for traceability</li>
 * </ol>
 *
 * <p><strong>Admin API</strong> (dev-only, no auth on this stub):
 * <ul>
 *   <li>{@code GET /api/v1/policies/stub/rules} — list current rules</li>
 *   <li>{@code POST /api/v1/policies/stub/rules} — add a rule</li>
 *   <li>{@code DELETE /api/v1/policies/stub/rules} — clear all rules</li>
 * </ul>
 *
 * <p><strong>Fail-closed contract for callers:</strong> treat any non-200 or
 * any response where {@code decision != "PERMIT"} as DENY.
 */
@RestController
@RequestMapping(path = "/api/v1/policies", produces = MediaType.APPLICATION_JSON_VALUE)
public class PolicyEvaluationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyEvaluationController.class);

    /** Default policy ID returned in PERMIT responses when no rule specifies one. */
    private static final UUID DEFAULT_POLICY_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001");

    private final StubProperties properties;

    /** Thread-safe list of runtime-configurable rules. */
    private final List<StubDecisionRule> runtimeRules = new CopyOnWriteArrayList<>();

    public PolicyEvaluationController(StubProperties properties) {
        this.properties = properties;
    }

    // -------------------------------------------------------------------------
    // Core contract endpoint — POST /api/v1/policies/evaluate
    // -------------------------------------------------------------------------

    /**
     * Evaluates a policy request and returns a PERMIT or DENY decision.
     *
     * <p>This is the endpoint all six dependent services call. The contract
     * (request/response shape) is frozen — changes require a version bump.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<PolicyEvaluationResponse> evaluate(
            @RequestBody PolicyEvaluationRequest request) {

        // Validate required fields — fail with 400 not 500
        String validationError = validate(request);
        if (validationError != null) {
            LOGGER.warn("SEC stub received invalid request: {}", validationError);
            return ResponseEntity.badRequest().build();
        }

        UUID requestId = UUID.randomUUID();
        PolicyEvaluationResponse response = resolveDecision(request, requestId);

        LOGGER.info("SEC STUB evaluate: tenant={} subject={} resource={}/{} action={} => {} [requestId={}]",
            request.getTenantId(), request.getSubjectId(),
            request.getResourceType(), request.getResourceId(),
            request.getAction(), response.getDecision(), requestId);

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Admin / test API — runtime rule management
    // -------------------------------------------------------------------------

    @GetMapping("/stub/rules")
    public ResponseEntity<List<StubDecisionRule>> listRules() {
        List<StubDecisionRule> all = new ArrayList<>(properties.getRules());
        all.addAll(runtimeRules);
        return ResponseEntity.ok(Collections.unmodifiableList(all));
    }

    @PostMapping("/stub/rules")
    public ResponseEntity<StubDecisionRule> addRule(@RequestBody StubDecisionRule rule) {
        runtimeRules.add(rule);
        LOGGER.info("SEC stub: runtime rule added: {}", rule);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/stub/rules")
    public ResponseEntity<Void> clearRules() {
        runtimeRules.clear();
        LOGGER.info("SEC stub: all runtime rules cleared");
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String validate(PolicyEvaluationRequest req) {
        if (req.getTenantId()    == null) return "tenantId is required";
        if (req.getSubjectId()   == null) return "subjectId is required";
        if (req.getResourceType() == null || req.getResourceType().isBlank())
            return "resourceType is required";
        if (req.getAction()      == null || req.getAction().isBlank())
            return "action is required";
        return null;
    }

    private PolicyEvaluationResponse resolveDecision(PolicyEvaluationRequest request,
                                                       UUID requestId) {
        // Runtime rules (added via admin API) take priority
        for (StubDecisionRule rule : runtimeRules) {
            if (rule.matches(request)) {
                return buildResponse(rule, requestId);
            }
        }

        // Static rules from application config
        for (StubDecisionRule rule : properties.getRules()) {
            if (rule.matches(request)) {
                return buildResponse(rule, requestId);
            }
        }

        // Global default
        String defaultDecision = properties.getDefaultDecision();
        if (PolicyEvaluationResponse.DECISION_PERMIT.equals(defaultDecision)) {
            return PolicyEvaluationResponse.permit(
                "Default stub decision: PERMIT (no matching rule)",
                DEFAULT_POLICY_ID, requestId);
        } else {
            return PolicyEvaluationResponse.deny(
                "Default stub decision: DENY (no matching rule)", requestId);
        }
    }

    private PolicyEvaluationResponse buildResponse(StubDecisionRule rule, UUID requestId) {
        if (PolicyEvaluationResponse.DECISION_PERMIT.equals(rule.getDecision())) {
            return PolicyEvaluationResponse.permit(
                rule.getReason() != null ? rule.getReason() : "Rule matched: PERMIT",
                rule.getPolicyId() != null ? rule.getPolicyId() : DEFAULT_POLICY_ID,
                requestId);
        } else {
            return PolicyEvaluationResponse.deny(
                rule.getReason() != null ? rule.getReason() : "Rule matched: DENY",
                requestId);
        }
    }
}
