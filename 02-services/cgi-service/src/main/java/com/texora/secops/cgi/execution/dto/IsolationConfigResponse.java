package com.texora.secops.cgi.execution.dto;

import com.texora.secops.cgi.execution.domain.IsolationConfigStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Worker isolation config as returned over HTTP.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class IsolationConfigResponse {

    private UUID id;

    private String handlerId;

    private String sandboxProfile;

    private boolean networkAllowed;

    private boolean runAsNonRoot;

    private IsolationConfigStatus status;

    private UUID submittedBy;

    private UUID decidedBy;

    private Instant decidedAt;

    private Instant createdAt;

    public IsolationConfigResponse() {
        // default constructor (JSON / property binding)
    }

    public IsolationConfigResponse(UUID id, String handlerId, String sandboxProfile, boolean networkAllowed, boolean runAsNonRoot, IsolationConfigStatus status, UUID submittedBy, UUID decidedBy, Instant decidedAt, Instant createdAt) {
        this.id = id;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IsolationConfigResponse)) {
            return false;
        }
        IsolationConfigResponse that = (IsolationConfigResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(handlerId, that.handlerId) && Objects.equals(sandboxProfile, that.sandboxProfile) && Objects.equals(networkAllowed, that.networkAllowed) && Objects.equals(runAsNonRoot, that.runAsNonRoot) && Objects.equals(status, that.status) && Objects.equals(submittedBy, that.submittedBy) && Objects.equals(decidedBy, that.decidedBy) && Objects.equals(decidedAt, that.decidedAt) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, handlerId, sandboxProfile, networkAllowed, runAsNonRoot, status, submittedBy, decidedBy, decidedAt, createdAt);
    }

    @Override
    public String toString() {
        return "IsolationConfigResponse{" + "id=" + id + ", handlerId=" + handlerId + ", sandboxProfile=" + sandboxProfile + ", networkAllowed=" + networkAllowed + ", runAsNonRoot=" + runAsNonRoot + ", status=" + status + ", submittedBy=" + submittedBy + ", decidedBy=" + decidedBy + ", decidedAt=" + decidedAt + ", createdAt=" + createdAt + "}";
    }

}
