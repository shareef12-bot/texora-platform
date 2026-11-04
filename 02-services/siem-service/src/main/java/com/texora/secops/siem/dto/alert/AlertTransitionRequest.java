package com.texora.secops.siem.dto.alert;

import com.texora.secops.siem.domain.enums.AlertStatus;
import jakarta.validation.constraints.NotNull;

public class AlertTransitionRequest {

    @NotNull
    private AlertStatus toStatus;

    private String notes;

    public AlertTransitionRequest() {
        // required for JSON deserialization
    }

    public AlertStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(AlertStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
