package com.texora.secops.cgi.execution.adapter;

/**
 * Isolation settings handed to the adapter. Immutable on purpose (no setters): nothing downstream may loosen
 * them after the service layer has decided them. The defaults are the non-negotiable ones.
 */
public final class IsolationSpec {

    private final String handlerId;
    private final String sandboxProfile;
    private final boolean networkAllowed;
    private final boolean runAsNonRoot;

    public IsolationSpec(String handlerId, String sandboxProfile, boolean networkAllowed, boolean runAsNonRoot) {
        this.handlerId = handlerId;
        this.sandboxProfile = sandboxProfile;
        this.networkAllowed = networkAllowed;
        this.runAsNonRoot = runAsNonRoot;
    }

    /** Sandboxed, non-root, NO network. */
    public static IsolationSpec strictDefaults(String handlerId, String sandboxProfile) {
        return new IsolationSpec(handlerId, sandboxProfile, false, true);
    }

    public String getHandlerId() {
        return handlerId;
    }

    public String getSandboxProfile() {
        return sandboxProfile;
    }

    public boolean isNetworkAllowed() {
        return networkAllowed;
    }

    public boolean isRunAsNonRoot() {
        return runAsNonRoot;
    }

    public boolean isStrict() {
        return !networkAllowed && runAsNonRoot;
    }

    @Override
    public String toString() {
        return "IsolationSpec{handlerId=" + handlerId + ", sandboxProfile=" + sandboxProfile
                + ", networkAllowed=" + networkAllowed + ", runAsNonRoot=" + runAsNonRoot + "}";
    }
}
