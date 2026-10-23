package com.texora.secops.vpn.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.domain.AuditEvent;
import com.texora.secops.vpn.domain.AuditOutbox;
import com.texora.secops.vpn.repository.AuditOutboxRepository;
import com.texora.secops.vpn.repository.VpnAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Durable local audit write for the VPN domain.
 *
 * WHY THIS IS LOCAL (KNOWN-INTEGRATION-PITFALLS #11 requires saying so): vpn_audit_event is mandated by the LLD as the
 * platform's immutable audit REFERENCE for network access, and VPN_TDD requires delivery that RETRIES instead of dropping.
 * This class is a writer, not a re-implementation of the shared AuditingAspect, and it provides the same guarantee the
 * pitfalls file requires of a local writer: (1) durable local write FIRST, in the SAME transaction as the state change,
 * (2) async Kafka publish, (3) scheduled retry (see {@link AuditOutboxPublisher}) for anything not yet published.
 *
 * Payloads are sanitized: any key that looks like secret material is replaced with a fixed marker, so a careless caller
 * cannot put a key, certificate private part, token or MFA code into the audit trail.
 */
@Component
public class VpnAuditRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(VpnAuditRecorder.class);
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(secret|password|passwd|token|credential|private|mfacode|apikey|api_key|authorization).*");
    private static final Set<String> NEVER_LOGGED_VALUE_HINTS = Set.of("-----BEGIN");

    private final VpnAuditEventRepository events;
    private final AuditOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public VpnAuditRecorder(VpnAuditEventRepository events, AuditOutboxRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Joins the caller's transaction when there is one, otherwise runs in its own. */
    @Transactional
    public AuditEvent record(UUID tenantId, UUID actorId, String action, String targetType, String targetId,
                             String outcome, Map<String, Object> payload) {
        Instant now = clock.instant();
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setActorId(actorId);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setOutcome(outcome);
        event.setOccurredAt(now);
        event.setPayload(toJson(sanitize(payload)));
        events.save(event);

        AuditOutbox box = new AuditOutbox();
        box.setId(UUID.randomUUID());
        box.setTenantId(tenantId);
        box.setAuditEventId(event.getId());
        box.setAttempts(0);
        box.setNextAttemptAt(now);
        box.setCreatedAt(now);
        outbox.save(box);
        return event;
    }

    Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (payload == null) {
            return clean;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (SECRET_KEY.matcher(key).matches() || looksLikeKeyMaterial(value)) {
                clean.put(key, "[REDACTED]");
            } else {
                clean.put(key, value);
            }
        }
        return clean;
    }

    private boolean looksLikeKeyMaterial(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String text = (String) value;
        for (String hint : NEVER_LOGGED_VALUE_HINTS) {
            if (text.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize audit payload; storing marker", e);
            return "{\"payloadError\":\"unserializable\"}";
        }
    }
}
