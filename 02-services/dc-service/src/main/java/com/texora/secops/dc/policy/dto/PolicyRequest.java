package com.texora.secops.dc.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class PolicyRequest {

    @NotBlank
    @Pattern(regexp = "USER|GROUP|ROLE")
    private String subjectType;

    @NotBlank
    @Size(max = 255)
    private String resource;

    @NotBlank
    @Size(max = 128)
    private String action;

    @NotBlank
    @Pattern(regexp = "ALLOW|DENY")
    private String effect;

    public PolicyRequest() {
        // required by Jackson
    }

    public PolicyRequest(String subjectType, String resource, String action, String effect) {
        this.subjectType = subjectType;
        this.resource = resource;
        this.action = action;
        this.effect = effect;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PolicyRequest that = (PolicyRequest) other;
        return Objects.equals(subjectType, that.subjectType) && Objects.equals(resource, that.resource) && Objects.equals(action, that.action) && Objects.equals(effect, that.effect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectType, resource, action, effect);
    }

    @Override
    public String toString() {
        return "PolicyRequest{subjectType=" + subjectType + ", " + "resource=" + resource + ", " + "action=" + action + ", " + "effect=" + effect + "}";
    }
}
