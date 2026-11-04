package com.texora.secops.vpn.session;

import java.time.Instant;

public class RevocationResult {

    private boolean found;

    private String status;

    private Instant revokedAt;

    private boolean dataplaneConfirmed;

    public RevocationResult() {
    }

    public RevocationResult(boolean found, String status, Instant revokedAt, boolean dataplaneConfirmed) {
        this.found = found;
        this.status = status;
        this.revokedAt = revokedAt;
        this.dataplaneConfirmed = dataplaneConfirmed;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isDataplaneConfirmed() {
        return dataplaneConfirmed;
    }

    public void setDataplaneConfirmed(boolean dataplaneConfirmed) {
        this.dataplaneConfirmed = dataplaneConfirmed;
    }

    @Override
    public String toString() {
        return "RevocationResult{" + "found=" + found + ", status=" + status + ", revokedAt=" + revokedAt + ", dataplaneConfirmed=" + dataplaneConfirmed + "}";
    }

}
