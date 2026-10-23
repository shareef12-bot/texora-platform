package com.texora.secops.ftp.security;

import java.util.Objects;

public class PolicyDecision {

    private boolean allowed;

    private boolean unavailable;

    private String reason;

    public PolicyDecision() {
    }

    public PolicyDecision(boolean allowed, boolean unavailable, String reason) {
        this.allowed = allowed;
        this.unavailable = unavailable;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyDecision)) {
            return false;
        }
        PolicyDecision that = (PolicyDecision) other;
        return Objects.equals(allowed, that.allowed) && Objects.equals(unavailable, that.unavailable) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, unavailable, reason);
    }

    @Override
    public String toString() {
        return "PolicyDecision{" + "allowed=" + allowed + ", unavailable=" + unavailable + ", reason=" + reason + "}";
    }
}
