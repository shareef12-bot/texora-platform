package com.texora.secops.ldap.schema.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapAttributeDefinition;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaApprovalService")
class SchemaApprovalServiceTest {

    @Mock
    private SchemaDefinitionRepository schemaRepo;
    @Mock
    private AttributeDefinitionRepository attributeRepo;
    @Mock
    private SchemaApprovalRepository approvalRepo;
    @Mock
    private LdapEngineAdapter ldapEngineAdapter;
    @Mock
    private LdapAuditHelper auditHelper;

    private SchemaApprovalService service;

    private final UUID tenantId   = UUID.randomUUID();
    private final UUID submitterId = UUID.randomUUID();
    private final UUID approverId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SchemaApprovalService(
                schemaRepo, attributeRepo, approvalRepo, ldapEngineAdapter, auditHelper);
    }

    // -------------------------------------------------------------------------
    // submitDefinition
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submitDefinition: saves DRAFT and returns response")
    void submitDefinition_happy() {
        when(ldapEngineAdapter.isValidSyntax(anyString())).thenReturn(true);
        when(schemaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attributeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("texoraUser");
        AttributeRequest attr = new AttributeRequest();
        attr.setName("uid");
        attr.setSyntax("1.3.6.1.4.1.1466.115.121.1.26");
        attr.setRequired(true);
        req.setAttributes(List.of(attr));

        SchemaDefinitionResponse resp = service.submitDefinition(req, submitterId, tenantId);

        assertThat(resp.getObjectClass()).isEqualTo("texoraUser");
        assertThat(resp.getStatus()).isEqualTo("DRAFT");
        verify(schemaRepo).save(any(LdapSchemaDefinition.class));
        verify(attributeRepo).save(any(LdapAttributeDefinition.class));
    }

    @Test
    @DisplayName("submitDefinition: rejects blank objectClass")
    void submitDefinition_blankObjectClass() {
        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("");
        req.setAttributes(List.of());

        assertThatThrownBy(() -> service.submitDefinition(req, submitterId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectClass");
    }

    @Test
    @DisplayName("submitDefinition: rejects objectClass with invalid characters")
    void submitDefinition_invalidObjectClass() {
        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("texora user"); // space is invalid
        req.setAttributes(List.of());

        assertThatThrownBy(() -> service.submitDefinition(req, submitterId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    @DisplayName("submitDefinition: rejects unknown LDAP syntax OID")
    void submitDefinition_invalidSyntax() {
        when(ldapEngineAdapter.isValidSyntax("not-an-oid")).thenReturn(false);

        SchemaDefinitionRequest req = new SchemaDefinitionRequest();
        req.setObjectClass("texoraUser");
        AttributeRequest attr = new AttributeRequest();
        attr.setName("uid");
        attr.setSyntax("not-an-oid");
        attr.setRequired(false);
        req.setAttributes(List.of(attr));

        assertThatThrownBy(() -> service.submitDefinition(req, submitterId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("syntax OID");
    }

    // -------------------------------------------------------------------------
    // Two-person rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordDecision: BLOCKS self-approval and audits it")
    void recordDecision_selfApprovalBlocked() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = pendingDef(schemaId, submitterId);
        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));

        SchemaDecisionRequest req = new SchemaDecisionRequest();
        req.setDecision("APPROVED");

        // submitterId tries to approve their own submission
        assertThatThrownBy(() -> service.recordDecision(schemaId, req, submitterId, tenantId))
                .isInstanceOf(SelfApprovalException.class)
                .hasMessageContaining("submitter cannot approve");

        // audit record for the blocked attempt MUST be created
        verify(auditHelper).record(any(), any(), anyString() /* SCHEMA_SELF_APPROVAL_BLOCKED */,
                any(), any(), any(), any());

        // engine must never be called
        verify(ldapEngineAdapter, never()).applySchemaChange(any(), any(), any());
    }

    @Test
    @DisplayName("recordDecision: different approver succeeds and applies to engine")
    void recordDecision_approvedByDifferentPrincipal() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = pendingDef(schemaId, submitterId);

        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));
        when(schemaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attributeRepo.findAllBySchemaDefinitionIdAndTenantId(schemaId, tenantId)).thenReturn(List.of());
        when(approvalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SchemaDecisionRequest req = new SchemaDecisionRequest();
        req.setDecision("APPROVED");
        req.setNotes("LGTM");

        SchemaDefinitionResponse resp = service.recordDecision(schemaId, req, approverId, tenantId);

        assertThat(resp.getStatus()).isEqualTo("APPROVED");
        verify(ldapEngineAdapter).applySchemaChange(any(), any(), any());
        verify(approvalRepo).save(any(LdapSchemaApproval.class));
    }

    @Test
    @DisplayName("recordDecision: REJECTED by different principal — engine never called")
    void recordDecision_rejectedByDifferentPrincipal() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = pendingDef(schemaId, submitterId);

        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));
        when(schemaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attributeRepo.findAllBySchemaDefinitionIdAndTenantId(schemaId, tenantId)).thenReturn(List.of());
        when(approvalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SchemaDecisionRequest req = new SchemaDecisionRequest();
        req.setDecision("REJECTED");
        req.setNotes("Not aligned with RFC 4512");

        SchemaDefinitionResponse resp = service.recordDecision(schemaId, req, approverId, tenantId);

        assertThat(resp.getStatus()).isEqualTo("REJECTED");
        // Engine must never be called on rejection
        verify(ldapEngineAdapter, never()).applySchemaChange(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Fail-closed: engine unreachable
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordDecision: engine unreachable → status stays PENDING_APPROVAL, exception propagates")
    void recordDecision_engineDown_failClosed() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = pendingDef(schemaId, submitterId);

        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));
        when(attributeRepo.findAllBySchemaDefinitionIdAndTenantId(schemaId, tenantId)).thenReturn(List.of());
        doThrow(new LdapEngineException("Connection refused")).when(ldapEngineAdapter).applySchemaChange(any(), any(), anyList());

        SchemaDecisionRequest req = new SchemaDecisionRequest();
        req.setDecision("APPROVED");

        assertThatThrownBy(() -> service.recordDecision(schemaId, req, approverId, tenantId))
                .isInstanceOf(LdapEngineException.class);

        // Verify status was NOT changed to APPROVED (fail closed)
        ArgumentCaptor<LdapSchemaDefinition> captor = ArgumentCaptor.forClass(LdapSchemaDefinition.class);
        verify(schemaRepo, never()).save(captor.capture());
    }

    // -------------------------------------------------------------------------
    // submitForApproval
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submitForApproval: non-submitter cannot submit for approval")
    void submitForApproval_wrongActor() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = draftDef(schemaId, submitterId);
        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.submitForApproval(schemaId, stranger, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("original submitter");
    }

    @Test
    @DisplayName("submitForApproval: only DRAFT can be submitted")
    void submitForApproval_wrongStatus() {
        UUID schemaId = UUID.randomUUID();
        LdapSchemaDefinition def = pendingDef(schemaId, submitterId);
        when(schemaRepo.findByIdAndTenantId(schemaId, tenantId)).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.submitForApproval(schemaId, submitterId, tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LdapSchemaDefinition draftDef(UUID id, UUID submitter) {
        LdapSchemaDefinition d = new LdapSchemaDefinition(
                id, tenantId, "texoraUser", LdapSchemaDefinition.Status.DRAFT, submitter, Instant.now());
        return d;
    }

    private LdapSchemaDefinition pendingDef(UUID id, UUID submitter) {
        LdapSchemaDefinition d = new LdapSchemaDefinition(
                id, tenantId, "texoraUser", LdapSchemaDefinition.Status.PENDING_APPROVAL, submitter, Instant.now());
        return d;
    }
}
