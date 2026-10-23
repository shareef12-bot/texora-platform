package com.texora.secops.cgi.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Two-person approval decision row (cgi_route_approval).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_route_approval", schema = "cgi")
public class RouteApproval {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "route_allowlist_entry_id", nullable = false, updatable = false)
    private UUID routeAllowlistEntryId;

    @Column(name = "approver_id", nullable = false, updatable = false)
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private DecisionType decision;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected RouteApproval() {
        // required by JPA
    }

    public RouteApproval(UUID id, UUID tenantId, UUID routeAllowlistEntryId, UUID approverId, DecisionType decision, Instant decidedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.routeAllowlistEntryId = routeAllowlistEntryId;
        this.approverId = approverId;
        this.decision = decision;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRouteAllowlistEntryId() {
        return routeAllowlistEntryId;
    }

    public void setRouteAllowlistEntryId(UUID routeAllowlistEntryId) {
        this.routeAllowlistEntryId = routeAllowlistEntryId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public void setApproverId(UUID approverId) {
        this.approverId = approverId;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteApproval)) {
            return false;
        }
        RouteApproval that = (RouteApproval) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RouteApproval{" + "id=" + id + ", tenantId=" + tenantId + ", routeAllowlistEntryId=" + routeAllowlistEntryId + ", approverId=" + approverId + ", decision=" + decision + ", decidedAt=" + decidedAt + "}";
    }

}
