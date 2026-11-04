package com.texora.secops.ldap.schema.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapAttributeDefinition;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.domain.LdapSchemaApproval;
import com.texora.secops.ldap.domain.LdapSchemaDefinition;
import com.texora.secops.ldap.exception.SelfApprovalException;
import com.texora.secops.ldap.repository.AttributeDefinitionRepository;
import com.texora.secops.ldap.repository.SchemaApprovalRepository;
import com.texora.secops.ldap.repository.SchemaDefinitionRepository;
import com.texora.secops.ldap.schema.dto.AttributeRequest;
import com.texora.secops.ldap.schema.dto.SchemaDecisionRequest;
import com.texora.secops.ldap.schema.dto.SchemaDefinitionRequest;
import com.texora.secops.ldap.schema.dto.SchemaDefinitionResponse;
import com.texora.secops.ldap.sync.client.LdapEngineAdapter;
import com.texora.secops.ldap.sync.client.LdapEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for LDAP schema object-class management, including the
 * two-person approval workflow.
 *
 * <p><b>Two-person rule:</b> the submitter of a schema definition can never be
 * its approver. Any attempt is rejected with {@link SelfApprovalException}.
 *
 * <p><b>Fail-closed:</b> if the LDAP engine is unreachable when applying an
 * approved schema change, the status is left as {@code PENDING_APPROVAL} and
 * {@link LdapEngineException} is re-thrown. The change is never silently dropped.
 */
@Service
public class SchemaApprovalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaApprovalService.class);

    /** Allowed characters in an LDAP object-class name (RFC 4512). */
    private static final java.util.regex.Pattern OBJECT_CLASS_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9\\-]*$");

    private final SchemaDefinitionRepository schemaRepo;
    private final AttributeDefinitionRepository attributeRepo;
    private final SchemaApprovalRepository approvalRepo;
    private final LdapEngineAdapter ldapEngineAdapter;
    private final LdapAuditHelper auditHelper;

    public SchemaApprovalService(SchemaDefinitionRepository schemaRepo,
                                  AttributeDefinitionRepository attributeRepo,
                                  SchemaApprovalRepository approvalRepo,
                                  LdapEngineAdapter ldapEngineAdapter,
                                  LdapAuditHelper auditHelper) {
        this.schemaRepo = schemaRepo;
        this.attributeRepo = attributeRepo;
        this.approvalRepo = approvalRepo;
        this.ldapEngineAdapter = ldapEngineAdapter;
        this.auditHelper = auditHelper;
    }

    /**
     * Submits a new schema definition as DRAFT after validating object class name and
     * attribute syntaxes.
     */
    @Transactional
    public SchemaDefinitionResponse submitDefinition(SchemaDefinitionRequest request,
                                                      UUID submittedBy, UUID tenantId) {
        validateObjectClass(request.getObjectClass());
        validateAttributes(request.getAttributes(), tenantId);

        UUID id = UUID.randomUUID();
        LdapSchemaDefinition definition = new LdapSchemaDefinition(
                id, tenantId, request.getObjectClass(),
                LdapSchemaDefinition.Status.DRAFT, submittedBy, Instant.now()
        );
        schemaRepo.save(definition);

        List<LdapAttributeDefinition> saved = new ArrayList<>();
        for (AttributeRequest attr : request.getAttributes()) {
            LdapAttributeDefinition attrDef = new LdapAttributeDefinition(
                    UUID.randomUUID(), id, tenantId, attr.getName(), attr.getSyntax(), attr.isRequired()
            );
            saved.add(attributeRepo.save(attrDef));
        }

        auditHelper.record(tenantId, submittedBy, "SCHEMA_SUBMITTED",
                "LdapSchemaDefinition", id.toString(), LdapAuditEvent.Outcome.SUCCESS, null);

        LOGGER.info("Schema definition {} submitted by {} for tenant {}", id, submittedBy, tenantId);
        return toResponse(definition, saved);
    }

    @Transactional(readOnly = true)
    public List<SchemaDefinitionResponse> listDefinitions(UUID tenantId) {
        return schemaRepo.findAllByTenantId(tenantId).stream()
                .map(def -> {
                    List<LdapAttributeDefinition> attrs =
                            attributeRepo.findAllBySchemaDefinitionIdAndTenantId(def.getId(), tenantId);
                    return toResponse(def, attrs);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SchemaDefinitionResponse getDefinition(UUID id, UUID tenantId) {
        LdapSchemaDefinition def = schemaRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Schema definition not found: " + id));
        List<LdapAttributeDefinition> attrs =
                attributeRepo.findAllBySchemaDefinitionIdAndTenantId(id, tenantId);
        return toResponse(def, attrs);
    }

    /**
     * Moves a DRAFT definition to PENDING_APPROVAL.
     * Only the original submitter may trigger this transition.
     */
    @Transactional
    public SchemaDefinitionResponse submitForApproval(UUID id, UUID requestedBy, UUID tenantId) {
        LdapSchemaDefinition def = schemaRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Schema definition not found: " + id));

        if (def.getStatusEnum() != LdapSchemaDefinition.Status.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT definitions can be submitted for approval, current status: " + def.getStatus());
        }
        if (!def.getSubmittedBy().equals(requestedBy)) {
            throw new IllegalArgumentException("Only the original submitter may submit for approval");
        }

        def.setStatusEnum(LdapSchemaDefinition.Status.PENDING_APPROVAL);
        schemaRepo.save(def);

        auditHelper.record(tenantId, requestedBy, "SCHEMA_SUBMITTED_FOR_APPROVAL",
                "LdapSchemaDefinition", id.toString(), LdapAuditEvent.Outcome.SUCCESS, null);

        List<LdapAttributeDefinition> attrs =
                attributeRepo.findAllBySchemaDefinitionIdAndTenantId(id, tenantId);
        return toResponse(def, attrs);
    }

    /**
     * Records an approval or rejection decision.
     *
     * <p><b>Two-person rule enforced here:</b> {@code approverId} must differ from
     * {@code definition.getSubmittedBy()}.
     *
     * <p><b>Fail-closed on engine error:</b> if the LDAP engine throws when applying
     * an APPROVED change, the definition stays PENDING_APPROVAL and the exception
     * propagates — the change is never silently ignored.
     */
    @Transactional
    public SchemaDefinitionResponse recordDecision(UUID schemaId, SchemaDecisionRequest request,
                                                    UUID approverId, UUID tenantId) {
        LdapSchemaDefinition def = schemaRepo.findByIdAndTenantId(schemaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Schema definition not found: " + schemaId));

        if (def.getStatusEnum() != LdapSchemaDefinition.Status.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL definitions can be decided, current status: " + def.getStatus());
        }

        // TWO-PERSON RULE — enforced in service layer, not just documentation
        if (def.getSubmittedBy().equals(approverId)) {
            auditHelper.record(tenantId, approverId, "SCHEMA_SELF_APPROVAL_BLOCKED",
                    "LdapSchemaDefinition", schemaId.toString(), LdapAuditEvent.Outcome.DENIED, null);
            throw new SelfApprovalException(
                    "The submitter cannot approve their own schema change. Submitter: " + def.getSubmittedBy());
        }

        LdapSchemaApproval.Decision decision;
        try {
            decision = LdapSchemaApproval.Decision.valueOf(request.getDecision().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }

        if (decision == LdapSchemaApproval.Decision.APPROVED) {
            // Apply to engine — fail closed if engine is unreachable
            List<LdapAttributeDefinition> attrs =
                    attributeRepo.findAllBySchemaDefinitionIdAndTenantId(schemaId, tenantId);
            applyToEngine(def, attrs); // throws LdapEngineException on failure — status stays PENDING
            def.setStatusEnum(LdapSchemaDefinition.Status.APPROVED);
        } else {
            def.setStatusEnum(LdapSchemaDefinition.Status.REJECTED);
            def.setRejectionReason(request.getNotes());
        }

        schemaRepo.save(def);

        LdapSchemaApproval approval = new LdapSchemaApproval(
                UUID.randomUUID(), schemaId, tenantId,
                approverId, decision, Instant.now(), request.getNotes()
        );
        approvalRepo.save(approval);

        auditHelper.record(tenantId, approverId,
                "SCHEMA_" + decision.name(),
                "LdapSchemaDefinition", schemaId.toString(),
                LdapAuditEvent.Outcome.SUCCESS, null);

        LOGGER.info("Schema {} decided {} by approver {} for tenant {}", schemaId, decision, approverId, tenantId);

        List<LdapAttributeDefinition> attrs =
                attributeRepo.findAllBySchemaDefinitionIdAndTenantId(schemaId, tenantId);
        return toResponse(def, attrs);
    }

    // --- private helpers ---

    private void validateObjectClass(String objectClass) {
        if (objectClass == null || objectClass.isBlank()) {
            throw new IllegalArgumentException("objectClass must not be blank");
        }
        if (!OBJECT_CLASS_PATTERN.matcher(objectClass).matches()) {
            throw new IllegalArgumentException(
                    "objectClass '" + objectClass + "' contains invalid characters; " +
                    "must match [A-Za-z][A-Za-z0-9\\-]*");
        }
    }

    private void validateAttributes(List<AttributeRequest> attributes, UUID tenantId) {
        if (attributes == null) {
            return;
        }
        for (AttributeRequest attr : attributes) {
            if (attr.getName() == null || attr.getName().isBlank()) {
                throw new IllegalArgumentException("Attribute name must not be blank");
            }
            if (attr.getSyntax() == null || attr.getSyntax().isBlank()) {
                throw new IllegalArgumentException("Attribute syntax must not be blank for: " + attr.getName());
            }
            // Validate syntax OID via engine adapter (stub always returns true)
            if (!ldapEngineAdapter.isValidSyntax(attr.getSyntax())) {
                throw new IllegalArgumentException(
                        "Unrecognized LDAP syntax OID '" + attr.getSyntax() + "' for attribute: " + attr.getName());
            }
        }
    }

    private void applyToEngine(LdapSchemaDefinition def, List<LdapAttributeDefinition> attrs) {
        Map<String, String> attrMap = new HashMap<>();
        List<String> requiredAttrs = new ArrayList<>();
        for (LdapAttributeDefinition attr : attrs) {
            attrMap.put(attr.getName(), attr.getSyntax());
            if (attr.isRequired()) {
                requiredAttrs.add(attr.getName());
            }
        }
        // Throws LdapEngineException if engine is unreachable — caller leaves status as PENDING_APPROVAL
        ldapEngineAdapter.applySchemaChange(def.getObjectClass(), attrMap, requiredAttrs);
    }

    private SchemaDefinitionResponse toResponse(LdapSchemaDefinition def,
                                                  List<LdapAttributeDefinition> attrs) {
        SchemaDefinitionResponse resp = new SchemaDefinitionResponse();
        resp.setId(def.getId());
        resp.setTenantId(def.getTenantId());
        resp.setObjectClass(def.getObjectClass());
        resp.setStatus(def.getStatus());
        resp.setSubmittedBy(def.getSubmittedBy());
        resp.setSubmittedAt(def.getSubmittedAt());
        resp.setRejectionReason(def.getRejectionReason());

        List<SchemaDefinitionResponse.AttributeResponse> attrResponses = new ArrayList<>();
        for (LdapAttributeDefinition attr : attrs) {
            SchemaDefinitionResponse.AttributeResponse ar = new SchemaDefinitionResponse.AttributeResponse();
            ar.setId(attr.getId());
            ar.setName(attr.getName());
            ar.setSyntax(attr.getSyntax());
            ar.setRequired(attr.isRequired());
            attrResponses.add(ar);
        }
        resp.setAttributes(attrResponses);
        return resp;
    }
}
