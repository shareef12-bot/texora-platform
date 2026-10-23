package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class QuarantineRecordResponse {

    private UUID id;

    private UUID transferJobId;

    private String reason;

    private Instant quarantinedAt;

    private UUID releasedBy;

    private Instant releasedAt;

    private String status;

    private List<InspectionResultResponse> inspections;

    public QuarantineRecordResponse() {
    }

    public QuarantineRecordResponse(UUID id, UUID transferJobId, String reason, Instant quarantinedAt, UUID releasedBy, Instant releasedAt, String status, List<InspectionResultResponse> inspections) {
        this.id = id;
        this.transferJobId = transferJobId;
        this.reason = reason;
        this.quarantinedAt = quarantinedAt;
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
        this.status = status;
        this.inspections = inspections;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTransferJobId() {
        return transferJobId;
    }

    public void setTransferJobId(UUID transferJobId) {
        this.transferJobId = transferJobId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public void setQuarantinedAt(Instant quarantinedAt) {
        this.quarantinedAt = quarantinedAt;
    }

    public UUID getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(UUID releasedBy) {
        this.releasedBy = releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<InspectionResultResponse> getInspections() {
        return inspections;
    }

    public void setInspections(List<InspectionResultResponse> inspections) {
        this.inspections = inspections;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuarantineRecordResponse)) {
            return false;
        }
        QuarantineRecordResponse that = (QuarantineRecordResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(transferJobId, that.transferJobId) && Objects.equals(reason, that.reason) && Objects.equals(quarantinedAt, that.quarantinedAt) && Objects.equals(releasedBy, that.releasedBy) && Objects.equals(releasedAt, that.releasedAt) && Objects.equals(status, that.status) && Objects.equals(inspections, that.inspections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transferJobId, reason, quarantinedAt, releasedBy, releasedAt, status, inspections);
    }

    @Override
    public String toString() {
        return "QuarantineRecordResponse{" + "id=" + id + ", transferJobId=" + transferJobId + ", reason=" + reason + ", quarantinedAt=" + quarantinedAt + ", releasedBy=" + releasedBy + ", releasedAt=" + releasedAt + ", status=" + status + ", inspections=" + inspections + "}";
    }
}
