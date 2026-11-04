package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PolicyDecisionRequest {

    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT")
    private String decision;

    @Size(max = 1000)
    private String comment;

    public PolicyDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "PolicyDecisionRequest{" + "decision=" + decision + ", comment=" + comment + "}";
    }

}
