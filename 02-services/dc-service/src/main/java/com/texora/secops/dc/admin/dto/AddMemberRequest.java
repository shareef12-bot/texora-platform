package com.texora.secops.dc.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public class AddMemberRequest {

    @NotNull
    private UUID userId;

    public AddMemberRequest() {
        // required by Jackson
    }

    public AddMemberRequest(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AddMemberRequest that = (AddMemberRequest) other;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "AddMemberRequest{userId=" + userId + "}";
    }
}
