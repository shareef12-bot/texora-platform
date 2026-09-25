package com.texora.secops.sec.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for POST /api/v1/policies/evaluate.
 * Called by all seven other services over mTLS.
 */
public class PolicyEvaluationRequest {

    @NotBlank
    private String callerService;

    @NotNull
    private SubjectDto subject;

    @NotBlank
    private String resource;

    @NotBlank
    private String action;

    private Map<String, Object> context;

    public PolicyEvaluationRequest() {}

    public PolicyEvaluationRequest(String callerService, SubjectDto subject,
                                   String resource, String action, Map<String, Object> context) {
        this.callerService = callerService;
        this.subject = subject;
        this.resource = resource;
        this.action = action;
        this.context = context;
    }

    public String getCallerService() { return callerService; }
    public void setCallerService(String callerService) { this.callerService = callerService; }

    public SubjectDto getSubject() { return subject; }
    public void setSubject(SubjectDto subject) { this.subject = subject; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public static class SubjectDto {
        private String type;
        private String id;

        public SubjectDto() {}
        public SubjectDto(String type, String id) { this.type = type; this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
