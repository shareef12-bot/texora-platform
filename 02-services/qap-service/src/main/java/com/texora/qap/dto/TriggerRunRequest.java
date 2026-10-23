package com.texora.qap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class TriggerRunRequest {

    @NotNull
    private UUID suiteId;

    @Pattern(regexp = "CI|SCHEDULE|MANUAL", message = "triggeredBy must be CI, SCHEDULE, or MANUAL")
    private String triggeredBy = "MANUAL";

    public TriggerRunRequest() {}

    public TriggerRunRequest(UUID suiteId, String triggeredBy) {
        this.suiteId = suiteId;
        this.triggeredBy = triggeredBy;
    }

    public UUID getSuiteId() { return suiteId; }
    public void setSuiteId(UUID suiteId) { this.suiteId = suiteId; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
}
