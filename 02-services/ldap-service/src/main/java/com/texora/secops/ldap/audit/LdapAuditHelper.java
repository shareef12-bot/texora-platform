package com.texora.secops.ldap.audit;

import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Helper for persisting local audit records to {@code ldap_audit_event}.
 *
 * <p>The shared {@code texora-audit-sdk}'s {@code AuditingAspect} handles publishing
 * events to Kafka. This helper covers the local durable-write side so every mutating
 * action has an immutable row in the DB regardless of Kafka availability.
 */
@Component
public class LdapAuditHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapAuditHelper.class);

    private final AuditEventRepository auditEventRepository;

    public LdapAuditHelper(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Saves a local audit event record.
     *
     * @param tenantId   tenant scope
     * @param actorId    UUID of the acting subject
     * @param action     action name (e.g. {@code SCHEMA_SUBMITTED}, {@code BIND_CONFIG_CREATED})
     * @param targetType resource type (e.g. {@code LdapSchemaDefinition})
     * @param targetId   resource identifier (may be null)
     * @param outcome    {@link LdapAuditEvent.Outcome}
     * @param payload    arbitrary JSON payload (may be null)
     */
    public void record(UUID tenantId, UUID actorId, String action, String targetType,
                       String targetId, LdapAuditEvent.Outcome outcome, Map<String, Object> payload) {
        try {
            LdapAuditEvent event = new LdapAuditEvent(
                    UUID.randomUUID(), tenantId, actorId, action,
                    targetType, targetId, outcome, Instant.now(), payload
            );
            auditEventRepository.save(event);
        } catch (Exception ex) {
            // Audit failure must not fail the business operation — log and continue.
            LOGGER.error("Failed to persist local audit event: action={} actor={} outcome={}",
                         action, actorId, outcome, ex);
        }
    }
}
