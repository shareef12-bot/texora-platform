package com.texora.secops.siem.normalization.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The COMMON normalized shape (LLD "NORMALIZED EVENT SCHEMA") that every upstream source is
 * mapped into by its {@code NormalizationProfile}, then indexed into the OpenSearch-compatible
 * event corpus and handed to {@code RuleExecutionSandbox}. actor/target/outcome are the common
 * field names correlation rules are written against, regardless of which of the twelve upstream
 * sources produced the underlying event.
 */
public class NormalizedEvent {

    private String id;
    private String sourceService;
    private String originalEventId;
    private String eventType;
    private String actor;
    private String target;
    private String outcome;
    private Instant occurredAt;
    private UUID tenantScope;
    private String productId;
    private Instant ingestedAt;
    private String rawPayload;

    public NormalizedEvent() {
        // required for JSON (de)serialization
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getOriginalEventId() {
        return originalEventId;
    }

    public void setOriginalEventId(String originalEventId) {
        this.originalEventId = originalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getTenantScope() {
        return tenantScope;
    }

    public void setTenantScope(UUID tenantScope) {
        this.tenantScope = tenantScope;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
