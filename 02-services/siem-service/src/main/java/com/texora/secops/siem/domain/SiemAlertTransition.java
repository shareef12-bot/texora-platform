package com.texora.secops.siem.domain;

import com.texora.secops.siem.domain.enums.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Full lifecycle history for one alert; append-only, one row per status transition. */
@Entity
@Table(name = "siem_alert_transition", schema = "siem")
public class SiemAlertTransition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 16)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private AlertStatus toStatus;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @Column(name = "notes")
    private String notes;

    protected SiemAlertTransition() {
        // required by JPA
    }

    public SiemAlertTransition(UUID id, UUID tenantId, UUID alertId, AlertStatus fromStatus,
                                AlertStatus toStatus, UUID actorId, Instant transitionedAt,
                                String notes) {
        this.id = id;
        this.tenantId = tenantId;
        this.alertId = alertId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.transitionedAt = transitionedAt;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public AlertStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(AlertStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public AlertStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(AlertStatus toStatus) {
        this.toStatus = toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public Instant getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(Instant transitionedAt) {
        this.transitionedAt = transitionedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SiemAlertTransition)) {
            return false;
        }
        SiemAlertTransition that = (SiemAlertTransition) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SiemAlertTransition{id=" + id + ", from=" + fromStatus + ", to=" + toStatus + "}";
    }
}
