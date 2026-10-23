package com.texora.secops.ftp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class ReleaseQuarantineRequest {

    @NotNull
    @Pattern(regexp = "^(RELEASE|DESTROY)$", message = "action must be RELEASE or DESTROY")
    private String action;

    @NotBlank
    @Size(max = 1000)
    private String justification;

    public ReleaseQuarantineRequest() {
    }

    public ReleaseQuarantineRequest(String action, String justification) {
        this.action = action;
        this.justification = justification;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReleaseQuarantineRequest)) {
            return false;
        }
        ReleaseQuarantineRequest that = (ReleaseQuarantineRequest) other;
        return Objects.equals(action, that.action) && Objects.equals(justification, that.justification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, justification);
    }

    @Override
    public String toString() {
        return "ReleaseQuarantineRequest{" + "action=" + action + ", justification=" + justification + "}";
    }
}
