package com.texora.secops.siem.dto.alert;

import com.texora.secops.siem.domain.SiemAlert;
import com.texora.secops.siem.domain.enums.AlertSeverity;
import com.texora.secops.siem.domain.enums.AlertStatus;

import java.time.Instant;
import java.util.UUID;

public class AlertResponse {

    private UUID id;
    private UUID correlationRuleId;
    private Instant triggeredAt;
    private AlertSeverity severity;
    private AlertStatus status;
    private String triggeringEventId;
    private String summary;

    public AlertResponse() {
        // required for JSON serialization
    }

    public static AlertResponse from(SiemAlert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setCorrelationRuleId(alert.getCorrelationRuleId());
        response.setTriggeredAt(alert.getTriggeredAt());
        response.setSeverity(alert.getSeverity());
        response.setStatus(alert.getStatus());
        response.setTriggeringEventId(alert.getTriggeringEventId());
        response.setSummary(alert.getSummary());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCorrelationRuleId() {
        return correlationRuleId;
    }

    public void setCorrelationRuleId(UUID correlationRuleId) {
        this.correlationRuleId = correlationRuleId;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getTriggeringEventId() {
        return triggeringEventId;
    }

    public void setTriggeringEventId(String triggeringEventId) {
        this.triggeringEventId = triggeringEventId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
