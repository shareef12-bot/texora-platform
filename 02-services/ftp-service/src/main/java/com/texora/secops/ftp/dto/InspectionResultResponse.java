package com.texora.secops.ftp.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class InspectionResultResponse {

    private UUID id;

    private String inspectionService;

    private String outcome;

    private String detail;

    private Instant inspectedAt;

    public InspectionResultResponse() {
    }

    public InspectionResultResponse(UUID id, String inspectionService, String outcome, String detail, Instant inspectedAt) {
        this.id = id;
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

    public String getInspectionService() {
        return inspectionService;
    }

    public void setInspectionService(String inspectionService) {
        this.inspectionService = inspectionService;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
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
        if (!(other instanceof InspectionResultResponse)) {
            return false;
        }
        InspectionResultResponse that = (InspectionResultResponse) other;
        return Objects.equals(id, that.id) && Objects.equals(inspectionService, that.inspectionService) && Objects.equals(outcome, that.outcome) && Objects.equals(detail, that.detail) && Objects.equals(inspectedAt, that.inspectedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, inspectionService, outcome, detail, inspectedAt);
    }

    @Override
    public String toString() {
        return "InspectionResultResponse{" + "id=" + id + ", inspectionService=" + inspectionService + ", outcome=" + outcome + ", detail=" + detail + ", inspectedAt=" + inspectedAt + "}";
    }
}
