  package com.texora.secops.sec.policy.service;

import com.texora.secops.sec.policy.domain.SecPolicyVersion;
import com.texora.secops.sec.policy.dto.PolicyEvaluationRequest;
import com.texora.secops.sec.policy.dto.PolicyEvaluationResponse;
import com.texora.secops.sec.policy.repository.SecPolicyEvaluationLogRepository;
import com.texora.secops.sec.policy.repository.SecPolicyVersionRepository;
import com.texora.secops.sec.policy.service.PolicyEvaluationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PolicyEvaluationService — the most critical service in the platform.
 *
 * <p>ALL five evaluation reason codes must be explicitly covered per §11 of the LLD.
 * Fault-injection tests prove EVALUATION_ERROR → DENY without relying on happy-path
 * assumptions (B.8, Req 1 from prompt).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyEvaluationService — Default Deny")
class PolicyEvaluationServiceTest {

    @Mock
    private SecPolicyVersionRepository policyVersionRepository;

    @Mock
    private SecPolicyEvaluationLogRepository evaluationLogRepository;

    private PolicyEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyEvaluationService(
                policyVersionRepository,
                evaluationLogRepository,
                new SimpleMeterRegistry());
    }

    // =====================================================================
    // determineDecision — pure unit tests, no I/O
    // =====================================================================

    @Nested
    @DisplayName("determineDecision (pure logic)")
    class DetermineDecisionTests {

        @Test
        @DisplayName("REASON 1: NO_MATCHING_POLICY → DENY when no active versions found")
        void noMatchingPolicy_returnsDeny() {
            PolicyEvaluationResponse response = service.determineDecision(Collections.emptyList());

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_NO_MATCHING_POLICY);
            assertThat(response.getPolicyVersionId()).isNull();
        }

        @Test
        @DisplayName("REASON 1: NO_MATCHING_POLICY → DENY when list is null")
        void nullList_returnsDenyNoMatchingPolicy() {
            PolicyEvaluationResponse response = service.determineDecision(null);

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_NO_MATCHING_POLICY);
        }

        @Test
        @DisplayName("REASON 2: EXPLICIT_DENY → DENY when any version has effect=DENY")
        void explicitDeny_returnsDeny() {
            SecPolicyVersion denyVersion = buildVersion(UUID.randomUUID(), "DENY");

            PolicyEvaluationResponse response = service.determineDecision(List.of(denyVersion));

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EXPLICIT_DENY);
        }

        @Test
        @DisplayName("REASON 2: DENY always wins — even when an ALLOW also applies")
        void denyWinsOverAllow() {
            SecPolicyVersion allowVersion = buildVersion(UUID.randomUUID(), "ALLOW");
            SecPolicyVersion denyVersion  = buildVersion(UUID.randomUUID(), "DENY");

            PolicyEvaluationResponse response = service.determineDecision(
                    List.of(allowVersion, denyVersion));

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EXPLICIT_DENY);
        }

        @Test
        @DisplayName("REASON 3: POLICY_CONFLICT → DENY when multiple ALLOW versions match")
        void policyConflict_returnsDeny() {
            SecPolicyVersion allow1 = buildVersion(UUID.randomUUID(), "ALLOW");
            SecPolicyVersion allow2 = buildVersion(UUID.randomUUID(), "ALLOW");

            PolicyEvaluationResponse response = service.determineDecision(List.of(allow1, allow2));

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_POLICY_CONFLICT);
        }

        @Test
        @DisplayName("REASON 4 (only ALLOW path): EXPLICIT_ALLOW → ALLOW when exactly one ALLOW, no DENY")
        void exactlyOneAllow_returnsAllow() {
            UUID versionId = UUID.randomUUID();
            SecPolicyVersion allowVersion = buildVersion(versionId, "ALLOW");

            PolicyEvaluationResponse response = service.determineDecision(List.of(allowVersion));

            assertThat(response.getDecision()).isEqualTo("ALLOW");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EXPLICIT_ALLOW);
            assertThat(response.getPolicyVersionId()).isEqualTo(versionId);
        }
    }

    // =====================================================================
    // evaluate() — FAULT INJECTION: proves exceptions yield DENY
    // =====================================================================

    @Nested
    @DisplayName("evaluate() — Fault Injection (EVALUATION_ERROR → DENY)")
    class FaultInjectionTests {

        @Test
        @DisplayName("REASON 5: EVALUATION_ERROR → DENY when repository throws RuntimeException")
        void repositoryException_yieldsDenyEvaluationError() {
            // Simulate the repository exploding mid-evaluation
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenThrow(new RuntimeException("Database connection lost"));

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            // The most dangerous bug in this platform is an exception that
            // the caller interprets as success — this test proves it cannot happen.
            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EVALUATION_ERROR);
        }

        @Test
        @DisplayName("EVALUATION_ERROR → DENY when repository throws checked-style exception via RuntimeException")
        void checkedStyleException_yieldsDeny() {
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenThrow(new RuntimeException("Timeout after 5000ms"));

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EVALUATION_ERROR);
            // Response must not be null — a null response would be interpreted as success by callers
            assertThat(response).isNotNull();
            assertThat(response.getEvaluatedAt()).isNotNull();
        }

        @Test
        @DisplayName("EVALUATION_ERROR → DENY when NullPointerException thrown internally")
        void nullPointerException_yieldsDeny() {
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenThrow(new NullPointerException("Unexpected null in policy graph"));

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_EVALUATION_ERROR);
        }

        @Test
        @DisplayName("evaluate() never returns null — callers must not get NPE from null response")
        void evaluate_neverReturnsNull() {
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenThrow(new RuntimeException("chaos"));

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            assertThat(response).isNotNull();
            assertThat(response.getDecision()).isEqualTo("DENY");
        }
    }

    // =====================================================================
    // Full evaluate() integration with mocked repo — happy path
    // =====================================================================

    @Nested
    @DisplayName("evaluate() — Happy paths via mocked repository")
    class EvaluateHappyPathTests {

        @Test
        @DisplayName("Returns ALLOW when exactly one ALLOW version matches")
        void evaluate_returnsAllow() {
            UUID versionId = UUID.randomUUID();
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenReturn(List.of(buildVersion(versionId, "ALLOW")));

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            assertThat(response.getDecision()).isEqualTo("ALLOW");
            assertThat(response.getPolicyVersionId()).isEqualTo(versionId);
        }

        @Test
        @DisplayName("Returns DENY/NO_MATCHING_POLICY when no versions match")
        void evaluate_returnsNoMatchingPolicy() {
            when(policyVersionRepository.findActiveVersionsForEvaluation(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            PolicyEvaluationResponse response = service.evaluate(
                    UUID.randomUUID(), buildRequest());

            assertThat(response.getDecision()).isEqualTo("DENY");
            assertThat(response.getReason()).isEqualTo(PolicyEvaluationService.REASON_NO_MATCHING_POLICY);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private SecPolicyVersion buildVersion(UUID id, String effect) {
        SecPolicyVersion v = new SecPolicyVersion(
                id, UUID.randomUUID(), UUID.randomUUID(), 1,
                effect, null, UUID.randomUUID());
        v.setStatus(SecPolicyVersion.STATUS_ACTIVE);
        return v;
    }

    private PolicyEvaluationRequest buildRequest() {
        PolicyEvaluationRequest req = new PolicyEvaluationRequest();
        req.setCallerService("sso");
        req.setSubject(new PolicyEvaluationRequest.SubjectDto("USER", UUID.randomUUID().toString()));
        req.setResource("sso:session");
        req.setAction("create");
        req.setContext(Map.of("mfaSatisfied", true));
        return req;
    }
}
