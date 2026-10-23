package com.texora.secops.ftp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;

public class PlaceQuarantineRequest {

    @NotNull
    private UUID transferJobId;

    @NotBlank
    @Size(max = 500)
    private String reason;

    public PlaceQuarantineRequest() {
    }

    public PlaceQuarantineRequest(UUID transferJobId, String reason) {
        this.transferJobId = transferJobId;
        this.reason = reason;
    }

    public UUID getTransferJobId() {
        return transferJobId;
    }

    public void setTransferJobId(UUID transferJobId) {
        this.transferJobId = transferJobId;
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
        if (!(other instanceof PlaceQuarantineRequest)) {
            return false;
        }
        PlaceQuarantineRequest that = (PlaceQuarantineRequest) other;
        return Objects.equals(transferJobId, that.transferJobId) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferJobId, reason);
    }

    @Override
    public String toString() {
        return "PlaceQuarantineRequest{" + "transferJobId=" + transferJobId + ", reason=" + reason + "}";
    }
}
