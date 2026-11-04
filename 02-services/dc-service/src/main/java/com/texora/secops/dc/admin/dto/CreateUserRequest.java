package com.texora.secops.dc.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class CreateUserRequest {

    @NotBlank
    @Size(max = 128)
    private String hrmsEmployeeId;

    @Size(max = 50)
    private List<String> initialGroups;

    public CreateUserRequest() {
        // required by Jackson
    }

    public CreateUserRequest(String hrmsEmployeeId, List<String> initialGroups) {
        this.hrmsEmployeeId = hrmsEmployeeId;
        this.initialGroups = initialGroups;
    }

    public String getHrmsEmployeeId() {
        return hrmsEmployeeId;
    }

    public void setHrmsEmployeeId(String hrmsEmployeeId) {
        this.hrmsEmployeeId = hrmsEmployeeId;
    }

    public List<String> getInitialGroups() {
        return initialGroups;
    }

    public void setInitialGroups(List<String> initialGroups) {
        this.initialGroups = initialGroups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        CreateUserRequest that = (CreateUserRequest) other;
        return Objects.equals(hrmsEmployeeId, that.hrmsEmployeeId) && Objects.equals(initialGroups, that.initialGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hrmsEmployeeId, initialGroups);
    }

    @Override
    public String toString() {
        return "CreateUserRequest{hrmsEmployeeId=" + hrmsEmployeeId + ", " + "initialGroups=" + initialGroups + "}";
    }
}
