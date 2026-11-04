package com.texora.secops.siem.dto.search;

import com.texora.secops.siem.normalization.model.NormalizedEvent;

import java.time.Instant;

public class NormalizedEventDto {

    private String id;
    private String sourceService;
    private String originalEventId;
    private String eventType;
    private String actor;
    private String target;
    private String outcome;
    private Instant occurredAt;
    private String productId;
    private Instant ingestedAt;

    public NormalizedEventDto() {
        // required for JSON serialization
    }

    public static NormalizedEventDto from(NormalizedEvent event) {
        NormalizedEventDto dto = new NormalizedEventDto();
        dto.setId(event.getId());
        dto.setSourceService(event.getSourceService());
        dto.setOriginalEventId(event.getOriginalEventId());
        dto.setEventType(event.getEventType());
        dto.setActor(event.getActor());
        dto.setTarget(event.getTarget());
        dto.setOutcome(event.getOutcome());
        dto.setOccurredAt(event.getOccurredAt());
        dto.setProductId(event.getProductId());
        dto.setIngestedAt(event.getIngestedAt());
        return dto;
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
}
