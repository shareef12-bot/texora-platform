package com.texora.qap.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateTestCaseRequest {

    @NotBlank
    private String name;

    private String steps;

    private String expectedResult;

    public CreateTestCaseRequest() {}

    public CreateTestCaseRequest(String name, String steps, String expectedResult) {
        this.name = name;
        this.steps = steps;
        this.expectedResult = expectedResult;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }

    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
}
