package com.texora.secops.dc.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateUserRequest {

    @NotNull
    private Boolean securityLocked;

    public UpdateUserRequest() {
        // required by Jackson
    }

    public UpdateUserRequest(Boolean securityLocked) {
        this.securityLocked = securityLocked;
    }

    public Boolean getSecurityLocked() {
        return securityLocked;
    }

    public void setSecurityLocked(Boolean securityLocked) {
        this.securityLocked = securityLocked;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        UpdateUserRequest that = (UpdateUserRequest) other;
        return Objects.equals(securityLocked, that.securityLocked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(securityLocked);
    }

    @Override
    public String toString() {
        return "UpdateUserRequest{securityLocked=" + securityLocked + "}";
    }
}
