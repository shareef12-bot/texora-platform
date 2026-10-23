package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class SessionSummary {

    private UUID sessionId;

    private UUID subjectId;

    private UUID accessPolicyId;

    private String status;

    private Instant establishedAt;

    private Instant expiresAt;

    private Instant revokedAt;

    private String revocationTrigger;

    public SessionSummary() {
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getAccessPolicyId() {
        return accessPolicyId;
    }

    public void setAccessPolicyId(UUID accessPolicyId) {
        this.accessPolicyId = accessPolicyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEstablishedAt() {
        return establishedAt;
    }

    public void setEstablishedAt(Instant establishedAt) {
        this.establishedAt = establishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationTrigger() {
        return revocationTrigger;
    }

    public void setRevocationTrigger(String revocationTrigger) {
        this.revocationTrigger = revocationTrigger;
    }

    @Override
    public String toString() {
        return "SessionSummary{" + "sessionId=" + sessionId + ", subjectId=" + subjectId + ", accessPolicyId=" + accessPolicyId + ", status=" + status + ", establishedAt=" + establishedAt + ", expiresAt=" + expiresAt + ", revokedAt=" + revokedAt + ", revocationTrigger=" + revocationTrigger + "}";
    }

}
