package com.texora.secops.siem.security;

import java.util.Map;

/** Request body sent to SEC's policy evaluation endpoint. Internal to PolicyDecisionClient. */
public class EvaluatePolicyRequest {

    private String callerService;
    private Subject subject;
    private String resource;
    private String action;
    private Map<String, Object> context;

    public EvaluatePolicyRequest() {
        // required for JSON (de)serialization
    }

    public EvaluatePolicyRequest(String callerService, Subject subject, String resource,
                                 String action, Map<String, Object> context) {
        this.callerService = callerService;
        this.subject = subject;
        this.resource = resource;
        this.action = action;
        this.context = context;
    }

    public String getCallerService() { return callerService; }
    public void setCallerService(String callerService) { this.callerService = callerService; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public static class Subject {
        private String type;
        private String id;

        public Subject() {
        }

        public Subject(String type, String id) {
            this.type = type;
            this.id = id;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
