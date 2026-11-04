package com.texora.secops.sec.approval.controller;

import com.texora.secops.sec.approval.domain.SecApprovalDecision;
import com.texora.secops.sec.approval.domain.SecPrivilegedRequest;
import com.texora.secops.sec.approval.service.PrivilegedApprovalService;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.sec.security.RbacInterceptor;
import com.texora.secops.starter.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET/POST /api/v1/privileged-approval-workflow
 * POST     /api/v1/privileged-approval-workflow/{id}/decision
 */
@RestController
@RequestMapping("/api/v1/privileged-approval-workflow")
public class PrivilegedApprovalController {

    private final PrivilegedApprovalService approvalService;

    public PrivilegedApprovalController(PrivilegedApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.approval.read')")
    public ResponseEntity<Page<SecPrivilegedRequest>> listRequests(Pageable pageable) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(approvalService.listRequests(tenantId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sec.approval.request')")
    public ResponseEntity<SecPrivilegedRequest> submitRequest(
            @Valid @RequestBody SubmitRequestBody body,
            @RequestAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE) TokenClaims claims) {

        UUID tenantId = TenantContext.get();
        UUID requestedBy = claims.getSubject();
        SecPrivilegedRequest request = approvalService.submitRequest(
                tenantId, body.getRequestType(), body.getTargetRef(),
                requestedBy, body.getJustification());
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('sec.approval.decide')")
    public ResponseEntity<SecApprovalDecision> recordDecision(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionBody body,
            @RequestAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE) TokenClaims claims) {

        UUID tenantId = TenantContext.get();
        UUID approverId = claims.getSubject();
        String approverRole = approverRoleOf(claims);

        SecApprovalDecision decision = approvalService.recordDecision(
                tenantId, id, approverId, body.getDecision(),
                approverRole, body.getComments());
        return ResponseEntity.ok(decision);
    }

    /**
     * The token carries a role SET (TokenClaims#getRoles), whereas the pre-refactor code read a
     * single "role" claim. Roles are joined in a stable (sorted) order and truncated to fit the
     * approver_role VARCHAR(128) column; "unknown" if the set is empty (as before when absent).
     */
    private static String approverRoleOf(TokenClaims claims) {
        if (claims.getRoles() == null || claims.getRoles().isEmpty()) {
            return "unknown";
        }
        String joined = String.join(",", new java.util.TreeSet<>(claims.getRoles()));
        return joined.length() > 128 ? joined.substring(0, 128) : joined;
    }

    // ------------------------------------------------------------------
    // Request DTOs
    // ------------------------------------------------------------------

    public static class SubmitRequestBody {
        @NotBlank private String requestType;
        @NotBlank private String targetRef;
        @NotBlank private String justification;

        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public String getTargetRef() { return targetRef; }
        public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
        public String getJustification() { return justification; }
        public void setJustification(String justification) { this.justification = justification; }
    }

    public static class DecisionBody {
        @NotBlank private String decision;
        private String comments;

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
    }
}
