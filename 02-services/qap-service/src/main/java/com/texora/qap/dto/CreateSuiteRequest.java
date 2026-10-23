package com.texora.qap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateSuiteRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String targetSystem;

    @NotBlank
    @Pattern(regexp = "UI|API", message = "suiteType must be UI or API")
    private String suiteType;

    private String status;

    public CreateSuiteRequest() {}

    public CreateSuiteRequest(String name, String targetSystem, String suiteType, String status) {
        this.name = name;
        this.targetSystem = targetSystem;
        this.suiteType = suiteType;
        this.status = status;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }

    public String getSuiteType() { return suiteType; }
    public void setSuiteType(String suiteType) { this.suiteType = suiteType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
