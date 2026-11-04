package com.texora.secops.cgi.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * POST /api/v1/worker-isolation request body. Omitted booleans take the SAFE defaults (no network, non-root).
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
public class IsolationConfigRequest {

    @NotBlank
    @Size(max = 128)
    private String handlerId;

    @NotBlank
    @Size(max = 64)
    private String sandboxProfile;

    private Boolean networkAllowed;

    private Boolean runAsNonRoot;

    public IsolationConfigRequest() {
        // default constructor (JSON / property binding)
    }

    public IsolationConfigRequest(String handlerId, String sandboxProfile, Boolean networkAllowed, Boolean runAsNonRoot) {
        this.handlerId = handlerId;
        this.sandboxProfile = sandboxProfile;
        this.networkAllowed = networkAllowed;
        this.runAsNonRoot = runAsNonRoot;
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

    public Boolean getNetworkAllowed() {
        return networkAllowed;
    }

    public void setNetworkAllowed(Boolean networkAllowed) {
        this.networkAllowed = networkAllowed;
    }

    public Boolean getRunAsNonRoot() {
        return runAsNonRoot;
    }

    public void setRunAsNonRoot(Boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IsolationConfigRequest)) {
            return false;
        }
        IsolationConfigRequest that = (IsolationConfigRequest) other;
        return Objects.equals(handlerId, that.handlerId) && Objects.equals(sandboxProfile, that.sandboxProfile) && Objects.equals(networkAllowed, that.networkAllowed) && Objects.equals(runAsNonRoot, that.runAsNonRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handlerId, sandboxProfile, networkAllowed, runAsNonRoot);
    }

    @Override
    public String toString() {
        return "IsolationConfigRequest{" + "handlerId=" + handlerId + ", sandboxProfile=" + sandboxProfile + ", networkAllowed=" + networkAllowed + ", runAsNonRoot=" + runAsNonRoot + "}";
    }

}
