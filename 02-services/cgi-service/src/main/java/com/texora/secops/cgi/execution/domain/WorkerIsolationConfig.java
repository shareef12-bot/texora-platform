package com.texora.secops.cgi.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-handler worker isolation settings (cgi_worker_isolation_config). Changes need two-person approval.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@Entity
@Table(name = "cgi_worker_isolation_config", schema = "cgi")
public class WorkerIsolationConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "handler_id", nullable = false, length = 128, updatable = false)
    private String handlerId;

    @Column(name = "sandbox_profile", nullable = false, length = 64, updatable = false)
    private String sandboxProfile;

    @Column(name = "network_allowed", nullable = false, updatable = false)
    private boolean networkAllowed;

    @Column(name = "run_as_non_root", nullable = false, updatable = false)
    private boolean runAsNonRoot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IsolationConfigStatus status;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "decided_by", nullable = true)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected WorkerIsolationConfig() {
        // required by JPA
    }

    public WorkerIsolationConfig(UUID id, UUID tenantId, String handlerId, String sandboxProfile, boolean networkAllowed, boolean runAsNonRoot, IsolationConfigStatus status, UUID submittedBy, UUID decidedBy, Instant decidedAt, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.handlerId = handlerId;
        this.sandboxProfile = sandboxProfile;
        this.networkAllowed = networkAllowed;
        this.runAsNonRoot = runAsNonRoot;
        this.status = status;
        this.submittedBy = submittedBy;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.createdAt = createdAt;
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

    public String getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(String handlerId) {
        this.handlerId = handlerId;
    }

    public String getSandboxProfile() {
        return sandboxProfile;
    }

    public void setSandboxProfile(String sandboxProfile) {
        this.sandboxProfile = sandboxProfile;
    }

    public boolean isNetworkAllowed() {
        return networkAllowed;
    }

    public void setNetworkAllowed(boolean networkAllowed) {
        this.networkAllowed = networkAllowed;
    }

    public boolean isRunAsNonRoot() {
        return runAsNonRoot;
    }

    public void setRunAsNonRoot(boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
    }

    public IsolationConfigStatus getStatus() {
        return status;
    }

    public void setStatus(IsolationConfigStatus status) {
        this.status = status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkerIsolationConfig)) {
            return false;
        }
        WorkerIsolationConfig that = (WorkerIsolationConfig) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkerIsolationConfig{" + "id=" + id + ", handlerId=" + handlerId + ", status=" + status + "}";
    }

}
