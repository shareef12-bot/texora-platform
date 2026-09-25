package com.texora.secops.sec.approval;

import com.texora.secops.sec.approval.domain.SecApprovalDecision;
import com.texora.secops.sec.approval.domain.SecPrivilegedRequest;
import com.texora.secops.sec.approval.repository.SecApprovalDecisionRepository;
import com.texora.secops.sec.approval.repository.SecPrivilegedRequestRepository;
import com.texora.secops.sec.approval.service.PrivilegedApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for dual-control approval — especially separation of duties.
 *
 * <p>CRITICAL: Self-approval must be rejected in CODE (B.6 §5, SEC-F-009).
 * These tests prove this cannot be bypassed by role assignment.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrivilegedApprovalService — Dual Control & Separation of Duties")
class PrivilegedApprovalServiceTest {

    @Mock
    private SecPrivilegedRequestRepository requestRepository;

    @Mock
    private SecApprovalDecisionRepository decisionRepository;

    private PrivilegedApprovalService service;

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID REQUESTER_ID = UUID.randomUUID();
    private static final UUID APPROVER_ID  = UUID.randomUUID();
    private static final UUID REQUEST_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PrivilegedApprovalService(requestRepository, decisionRepository);
        ReflectionTestUtils.setField(service, "approvalExpiryHours", 48);
    }

    @Test
    @DisplayName("Self-approval REJECTED with 403 — requester cannot be approver")
    void selfApproval_rejected() {
        SecPrivilegedRequest pending = buildPendingRequest(REQUEST_ID, REQUESTER_ID);
        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(pending));

        // The requester tries to approve their own request — must be 403
        assertThatThrownBy(() ->
            service.recordDecision(TENANT_ID, REQUEST_ID,
                    REQUESTER_ID,   // <-- same as requestedBy: separation of duties violation
                    "APPROVED", "sec.policy.activate", "Approving my own request"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).contains("Separation of duties");
                });
    }

    @Test
    @DisplayName("Distinct approver can approve a pending request")
    void distinctApprover_canApprove() {
        SecPrivilegedRequest pending = buildPendingRequest(REQUEST_ID, REQUESTER_ID);
        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(pending));
        when(decisionRepository.findByPrivilegedRequestIdAndApproverId(REQUEST_ID, APPROVER_ID))
                .thenReturn(Optional.empty());
        when(decisionRepository.countByPrivilegedRequestIdAndDecision(REQUEST_ID, "APPROVED"))
                .thenReturn(1L);

        SecApprovalDecision decision = buildDecision();
        when(decisionRepository.save(any())).thenReturn(decision);
        when(requestRepository.save(any())).thenReturn(pending);

        SecApprovalDecision result = service.recordDecision(
                TENANT_ID, REQUEST_ID, APPROVER_ID, "APPROVED",
                "sec.policy.activate", "Approved after review");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Request with blank justification is rejected")
    void blankJustification_rejected() {
        assertThatThrownBy(() ->
            service.submitRequest(TENANT_ID, "POLICY_ACTIVATION", "policy-version:123",
                    REQUESTER_ID, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("Null justification is rejected")
    void nullJustification_rejected() {
        assertThatThrownBy(() ->
            service.submitRequest(TENANT_ID, "POLICY_ACTIVATION", "policy-version:123",
                    REQUESTER_ID, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("Decision on non-PENDING request is rejected")
    void nonPendingRequest_decisionRejected() {
        SecPrivilegedRequest approved = buildPendingRequest(REQUEST_ID, REQUESTER_ID);
        approved.setStatus(SecPrivilegedRequest.STATUS_APPROVED);

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(approved));

        assertThatThrownBy(() ->
            service.recordDecision(TENANT_ID, REQUEST_ID, APPROVER_ID,
                    "APPROVED", "sec.policy.activate", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("Expired request cannot be decided")
    void expiredRequest_decisionRejected() {
        SecPrivilegedRequest pending = buildPendingRequest(REQUEST_ID, REQUESTER_ID);
        // Set expiry in the past
        pending.setExpiresAt(Instant.now().minusSeconds(3600));

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(pending));
        when(requestRepository.save(any())).thenReturn(pending);

        assertThatThrownBy(() ->
            service.recordDecision(TENANT_ID, REQUEST_ID, APPROVER_ID,
                    "APPROVED", "sec.policy.activate", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                    assertThat(rse.getReason()).containsIgnoringCase("expired");
                });
    }

    @Test
    @DisplayName("Duplicate approver decision is rejected with 409")
    void duplicateApproverDecision_rejected() {
        SecPrivilegedRequest pending = buildPendingRequest(REQUEST_ID, REQUESTER_ID);
        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(pending));
        when(decisionRepository.findByPrivilegedRequestIdAndApproverId(REQUEST_ID, APPROVER_ID))
                .thenReturn(Optional.of(buildDecision()));

        assertThatThrownBy(() ->
            service.recordDecision(TENANT_ID, REQUEST_ID, APPROVER_ID,
                    "APPROVED", "sec.policy.activate", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SecPrivilegedRequest buildPendingRequest(UUID id, UUID requestedBy) {
        SecPrivilegedRequest r = new SecPrivilegedRequest(
                id, TENANT_ID, "POLICY_ACTIVATION", "policy-version:abc",
                requestedBy, "Activating critical policy", Instant.now().plusSeconds(86400));
        return r;
    }

    private SecApprovalDecision buildDecision() {
        return new SecApprovalDecision(UUID.randomUUID(), TENANT_ID, REQUEST_ID,
                APPROVER_ID, "APPROVED", "sec.policy.activate", null);
    }
}
