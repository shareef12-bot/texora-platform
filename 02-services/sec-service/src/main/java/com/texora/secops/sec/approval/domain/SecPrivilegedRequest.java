package com.texora.secops.sec.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A privileged request requiring dual-control approval.
 *
 * <p>Requester can NEVER be the approver — enforced in PrivilegedApprovalService,
 * not in documentation (B.6 §5, SEC-F-009).</p>
 */
@Entity
@Table(name = "sec_privileged_request", schema = "sec")
public class SecPrivilegedRequest {

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED  = "EXPIRED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_type", nullable = false, length = 64)
    private String requestType;

    @Column(name = "target_ref", nullable = false, length = 512)
    private String targetRef;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected SecPrivilegedRequest() {
        // required by JPA
    }

    public SecPrivilegedRequest(UUID id, UUID tenantId, String requestType, String targetRef,
                                 UUID requestedBy, String justification, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestType = requestType;
        this.targetRef = targetRef;
        this.requestedBy = requestedBy;
        this.justification = justification;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecPrivilegedRequest)) return false;
        SecPrivilegedRequest that = (SecPrivilegedRequest) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecPrivilegedRequest{id=" + id + ", type='" + requestType
                + "', status='" + status + "', requestedBy=" + requestedBy + "}";
    }
}
