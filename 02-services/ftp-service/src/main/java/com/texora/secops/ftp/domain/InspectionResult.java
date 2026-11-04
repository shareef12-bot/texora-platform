package com.texora.secops.ftp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ftp_inspection_result", schema = "ftp")
public class InspectionResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transfer_job_id", nullable = false, updatable = false)
    private UUID transferJobId;

    @Column(name = "quarantine_record_id")
    private UUID quarantineRecordId;

    @Column(name = "inspection_service", length = 128, nullable = false, updatable = false)
    private String inspectionService;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 16, nullable = false, updatable = false)
    private InspectionOutcome outcome;

    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @Column(name = "inspected_at", nullable = false, updatable = false)
    private Instant inspectedAt;

    protected InspectionResult() { // required by JPA
    }

    public InspectionResult(UUID id, UUID tenantId, UUID transferJobId, UUID quarantineRecordId, String inspectionService, InspectionOutcome outcome, String detail, Instant inspectedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.transferJobId = transferJobId;
        this.quarantineRecordId = quarantineRecordId;
        this.inspectionService = inspectionService;
        this.outcome = outcome;
        this.detail = detail;
        this.inspectedAt = inspectedAt;
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

    public UUID getTransferJobId() {
        return transferJobId;
    }

    public void setTransferJobId(UUID transferJobId) {
        this.transferJobId = transferJobId;
    }

    public UUID getQuarantineRecordId() {
        return quarantineRecordId;
    }

    public void setQuarantineRecordId(UUID quarantineRecordId) {
        this.quarantineRecordId = quarantineRecordId;
    }

    public String getInspectionService() {
        return inspectionService;
    }

    public void setInspectionService(String inspectionService) {
        this.inspectionService = inspectionService;
    }

    public InspectionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(InspectionOutcome outcome) {
        this.outcome = outcome;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(Instant inspectedAt) {
        this.inspectedAt = inspectedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InspectionResult)) {
            return false;
        }
        InspectionResult that = (InspectionResult) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InspectionResult{" + "id=" + id + ", transferJobId=" + transferJobId + ", outcome=" + outcome + "}";
    }
}
