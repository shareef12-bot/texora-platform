package com.texora.secops.vpn.dto;

import java.time.Instant;
import java.util.UUID;

public class SubjectResponse {

    private UUID id;

    private UUID dcUserId;

    private String status;

    private Instant identitySyncedAt;

    private Instant createdAt;

    public SubjectResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public void setDcUserId(UUID dcUserId) {
        this.dcUserId = dcUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getIdentitySyncedAt() {
        return identitySyncedAt;
    }

    public void setIdentitySyncedAt(Instant identitySyncedAt) {
        this.identitySyncedAt = identitySyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SubjectResponse{" + "id=" + id + ", dcUserId=" + dcUserId + ", status=" + status + ", identitySyncedAt=" + identitySyncedAt + ", createdAt=" + createdAt + "}";
    }

}
