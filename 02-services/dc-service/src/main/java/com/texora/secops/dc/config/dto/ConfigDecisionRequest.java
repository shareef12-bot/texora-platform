package com.texora.secops.dc.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ConfigDecisionRequest {

    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED")
    private String decision;

    @Size(max = 1000)
    private String reason;

    public ConfigDecisionRequest() {
        // required by Jackson
    }

    public ConfigDecisionRequest(String decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
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
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ConfigDecisionRequest that = (ConfigDecisionRequest) other;
        return Objects.equals(decision, that.decision) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, reason);
    }

    @Override
    public String toString() {
        return "ConfigDecisionRequest{decision=" + decision + ", " + "reason=" + reason + "}";
    }
}
