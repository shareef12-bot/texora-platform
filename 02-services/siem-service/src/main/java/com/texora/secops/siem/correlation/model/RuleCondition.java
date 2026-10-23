package com.texora.secops.siem.correlation.model;

/** One stage of a correlation rule's condition chain, e.g. "5 ldap bind.failed events within
 *  300 seconds", optionally followed by a chained condition that must occur within a further
 *  window. See RuleExecutionSandbox for how a chain of these is evaluated. */
public class RuleCondition {

    private String sourceService;
    private String eventType;
    private Integer countThreshold;
    private Integer windowSeconds;
    private Integer withinSeconds;

    public RuleCondition() {
        // required for JSON (de)serialization
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getCountThreshold() {
        return countThreshold;
    }

    public void setCountThreshold(Integer countThreshold) {
        this.countThreshold = countThreshold;
    }

    public Integer getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(Integer windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Integer getWithinSeconds() {
        return withinSeconds;
    }

    public void setWithinSeconds(Integer withinSeconds) {
        this.withinSeconds = withinSeconds;
    }
}
