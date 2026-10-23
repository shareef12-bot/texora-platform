package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class SubjectRequest {

    @NotNull
    private UUID dcUserId;

    public SubjectRequest() {
    }

    public UUID getDcUserId() {
        return dcUserId;
    }

    public void setDcUserId(UUID dcUserId) {
        this.dcUserId = dcUserId;
    }

    @Override
    public String toString() {
        return "SubjectRequest{" + "dcUserId=" + dcUserId + "}";
    }

}
