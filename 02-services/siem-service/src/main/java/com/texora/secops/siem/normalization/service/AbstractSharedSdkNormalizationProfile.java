package com.texora.secops.siem.normalization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.siem.normalization.model.NormalizedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Every other Texora SecOps service (LDAP, SSO, FTP, CGI, VPN, SEC) and, by platform
 * convention, the five business products all emit their audit events through the same shared
 * Audit SDK described in the shared engineering standards - i.e. the same underlying JSON shape
 * ({@code id, tenantId, actorId, action, targetType, targetId, outcome, occurredAt, payload}) on
 * every {@code *.audit.events} topic. This base class implements that common mapping once;
 * concrete profiles only declare their {@code sourceKey()}/{@code productId()} and override
 * {@link #mapEventType} or {@link #mapTarget} where a specific source's field naming genuinely
 * diverges from the shared convention. This keeps "one profile per source" true (twelve
 * independent, individually swappable Spring beans) without duplicating parsing logic twelve
 * times.
 */
public abstract class AbstractSharedSdkNormalizationProfile implements NormalizationProfile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public NormalizedEvent normalize(RawEventContext context) throws NormalizationException {
        JsonNode node;
        try {
            node = MAPPER.readTree(context.getRawPayload());
        } catch (Exception ex) {
            throw new NormalizationException(
                    "Malformed JSON payload from source " + sourceKey() + ": " + ex.getMessage(), ex);
        }

        NormalizedEvent event = new NormalizedEvent();
        event.setId(UUID.randomUUID().toString());
        event.setSourceService(sourceKey());
        event.setProductId(productId());
        event.setOriginalEventId(firstNonNullText(node, "id", "eventId"));
        event.setEventType(mapEventType(node));
        event.setActor(firstNonNullText(node, "actorId", "actor"));
        event.setTarget(mapTarget(node));
        event.setOutcome(firstNonNullText(node, "outcome"));
        event.setOccurredAt(parseInstant(firstNonNullText(node, "occurredAt", "timestamp")));
        event.setTenantScope(parseUuid(firstNonNullText(node, "tenantId", "tenant_id")));
        event.setIngestedAt(context.getIngestedAt());
        event.setRawPayload(context.getRawPayload());

        if (event.getTenantScope() == null) {
            throw new NormalizationException(
                    "Event from source " + sourceKey() + " has no resolvable tenant - cannot index without a tenant scope");
        }
        return event;
    }

    /** Override when a source's action field needs source-specific interpretation. */
    protected String mapEventType(JsonNode node) {
        return firstNonNullText(node, "action", "eventType");
    }

    /** Override when a source's target shape needs source-specific interpretation. */
    protected String mapTarget(JsonNode node) {
        String targetType = firstNonNullText(node, "targetType");
        String targetId = firstNonNullText(node, "targetId");
        if (targetType == null && targetId == null) {
            return null;
        }
        return (targetType == null ? "unknown" : targetType) + ":" + (targetId == null ? "unknown" : targetId);
    }

    private String firstNonNullText(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private Instant parseInstant(String text) {
        if (text == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private UUID parseUuid(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
