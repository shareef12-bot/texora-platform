package com.texora.secops.sec.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sec_approval_decision", schema = "sec")
public class SecApprovalDecision {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "privileged_request_id", nullable = false)
    private UUID privilegedRequestId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "approver_role", nullable = false, length = 128)
    private String approverRole;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    protected SecApprovalDecision() {
        // required by JPA
    }

    public SecApprovalDecision(UUID id, UUID tenantId, UUID privilegedRequestId,
                                UUID approverId, String decision, String approverRole, String comments) {
        this.id = id;
        this.tenantId = tenantId;
        this.privilegedRequestId = privilegedRequestId;
        this.approverId = approverId;
        this.decision = decision;
        this.decidedAt = Instant.now();
        this.approverRole = approverRole;
        this.comments = comments;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getPrivilegedRequestId() { return privilegedRequestId; }
    public void setPrivilegedRequestId(UUID privilegedRequestId) { this.privilegedRequestId = privilegedRequestId; }

    public UUID getApproverId() { return approverId; }
    public void setApproverId(UUID approverId) { this.approverId = approverId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecApprovalDecision)) return false;
        SecApprovalDecision that = (SecApprovalDecision) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecApprovalDecision{id=" + id + ", requestId=" + privilegedRequestId
                + ", approverId=" + approverId + ", decision='" + decision + "'}";
    }
}
