package com.texora.secops.ftp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;

public class SubmitTransferJobRequest {

    @NotNull
    private UUID sourceEndpointId;

    @NotNull
    private UUID destEndpointId;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "fileName may contain only letters, digits, dot, underscore and hyphen")
    private String fileName;

    private Boolean requireInspection;

    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "expectedSha256 must be 64 hex characters")
    private String expectedSha256;

    public SubmitTransferJobRequest() {
    }

    public SubmitTransferJobRequest(UUID sourceEndpointId, UUID destEndpointId, String fileName, Boolean requireInspection, String expectedSha256) {
        this.sourceEndpointId = sourceEndpointId;
        this.destEndpointId = destEndpointId;
        this.fileName = fileName;
        this.requireInspection = requireInspection;
        this.expectedSha256 = expectedSha256;
    }

    public UUID getSourceEndpointId() {
        return sourceEndpointId;
    }

    public void setSourceEndpointId(UUID sourceEndpointId) {
        this.sourceEndpointId = sourceEndpointId;
    }

    public UUID getDestEndpointId() {
        return destEndpointId;
    }

    public void setDestEndpointId(UUID destEndpointId) {
        this.destEndpointId = destEndpointId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Boolean getRequireInspection() {
        return requireInspection;
    }

    public void setRequireInspection(Boolean requireInspection) {
        this.requireInspection = requireInspection;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubmitTransferJobRequest)) {
            return false;
        }
        SubmitTransferJobRequest that = (SubmitTransferJobRequest) other;
        return Objects.equals(sourceEndpointId, that.sourceEndpointId) && Objects.equals(destEndpointId, that.destEndpointId) && Objects.equals(fileName, that.fileName) && Objects.equals(requireInspection, that.requireInspection) && Objects.equals(expectedSha256, that.expectedSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceEndpointId, destEndpointId, fileName, requireInspection, expectedSha256);
    }

    @Override
    public String toString() {
        return "SubmitTransferJobRequest{" + "sourceEndpointId=" + sourceEndpointId + ", destEndpointId=" + destEndpointId + ", fileName=" + fileName + ", requireInspection=" + requireInspection + ", expectedSha256=" + expectedSha256 + "}";
    }
}
