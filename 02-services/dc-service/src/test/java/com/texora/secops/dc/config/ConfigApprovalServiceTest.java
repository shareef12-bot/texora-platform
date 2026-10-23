package com.texora.secops.dc.config;

import com.texora.secops.dc.TestSupport;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.config.service.ConfigApprovalService;
import com.texora.secops.dc.domain.DcConfigApproval;
import com.texora.secops.dc.domain.DcConfigItem;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.repository.ConfigApprovalRepository;
import com.texora.secops.dc.repository.ConfigItemRepository;
import com.texora.secops.dc.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigApprovalServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID submitter = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    private ConfigItemRepository items;
    private ConfigApprovalRepository approvals;
    private DcAuditRecorder audit;
    private ConfigApprovalService service;

    @BeforeEach
    void setUp() {
        items = mock(ConfigItemRepository.class);
        approvals = mock(ConfigApprovalRepository.class);
        audit = mock(DcAuditRecorder.class);
        service = new ConfigApprovalService(items, approvals, audit, TestSupport.passThroughLookup(), TestSupport.fixedTime(), 3, 24);
    }

    private DcConfigItem pending() {
        DcConfigItem i = new DcConfigItem();
        i.setId(UUID.randomUUID());
        i.setTenantId(tenant);
        i.setConfigKey("session.timeout");
        i.setConfigValue("30");
        i.setStatus("PENDING_APPROVAL");
        i.setSubmittedBy(submitter);
        when(items.findByIdForUpdate(tenant, i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    private CallerContext as(UUID subject) {
        return TestSupport.caller(tenant, subject, "dc.config.approve");
    }

    @Test
    void submitMovesDraftToPendingApprovalAndAuditsBothTransitions() {
        DcConfigItem item = service.submit(as(submitter), "session.timeout", "30");
        assertEquals("PENDING_APPROVAL", item.getStatus());
        assertEquals(submitter, item.getSubmittedBy());
        verify(audit).record(eq(tenant), eq(submitter), eq("CONFIG_DRAFTED"), anyString(), anyString(), anyString(), anyMap());
        verify(audit).record(eq(tenant), eq(submitter), eq("CONFIG_PENDING_APPROVAL"), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void secretLookingKeysAreRejected() {
        DcException e = assertThrows(DcException.class, () -> service.submit(as(submitter), "db.password", "x"));
        assertEquals("SECRET_IN_CONFIG", e.getCode());
        verify(items, never()).save(any());
    }

    @Test
    void submitterCanNeverDecideOwnChange() {
        DcConfigItem item = pending();
        DcException e = assertThrows(DcException.class, () -> service.decide(as(submitter), item.getId(), "APPROVED", null));
        assertEquals(403, e.getStatus().value());
        assertEquals("SELF_APPROVAL_FORBIDDEN", e.getCode());
        verify(approvals, never()).save(any());
        assertEquals("PENDING_APPROVAL", item.getStatus());
        verify(audit).recordIndependent(eq(tenant), eq(submitter), eq("CONFIG_DECISION_DENIED"), anyString(), anyString(), eq("DENIED"), anyMap());
    }

    @Test
    void selfRejectionIsAlsoBlocked() {
        DcConfigItem item = pending();
        assertThrows(DcException.class, () -> service.decide(as(submitter), item.getId(), "REJECTED", "nope"));
        assertEquals("PENDING_APPROVAL", item.getStatus());
    }

    @Test
    void secondApproverActivatesValue() {
        DcConfigItem item = pending();
        DcConfigItem result = service.decide(as(approver), item.getId(), "APPROVED", null);
        assertEquals("APPROVED", result.getStatus());
        assertNotNull(result.getActivatedAt());
        verify(approvals).save(any(DcConfigApproval.class));
        verify(audit).record(eq(tenant), eq(approver), eq("CONFIG_APPROVED"), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void rejectionRequiresReasonAndRecordsIt() {
        DcConfigItem item = pending();
        DcException e = assertThrows(DcException.class, () -> service.decide(as(approver), item.getId(), "REJECTED", " "));
        assertEquals("REASON_REQUIRED", e.getCode());
        DcConfigItem result = service.decide(as(approver), item.getId(), "REJECTED", "too risky");
        assertEquals("REJECTED", result.getStatus());
        assertEquals("too risky", result.getRejectionReason());
    }

    @Test
    void alreadyDecidedItemConflicts() {
        DcConfigItem item = pending();
        item.setStatus("APPROVED");
        assertEquals("NOT_PENDING", assertThrows(DcException.class, () -> service.decide(as(approver), item.getId(), "APPROVED", null)).getCode());
    }

    @Test
    void sameApproverCannotDecideTwice() {
        DcConfigItem item = pending();
        when(approvals.existsByTenantIdAndConfigItemIdAndApproverId(tenant, item.getId(), approver)).thenReturn(true);
        assertEquals("ALREADY_DECIDED", assertThrows(DcException.class, () -> service.decide(as(approver), item.getId(), "APPROVED", null)).getCode());
    }

    @Test
    void invalidDecisionIsRejected() {
        DcConfigItem item = pending();
        assertEquals("INVALID_DECISION", assertThrows(DcException.class, () -> service.decide(as(approver), item.getId(), "MAYBE", null)).getCode());
    }

    @Test
    void unknownItemIsNotFound() {
        assertEquals(404, assertThrows(DcException.class, () -> service.decide(as(approver), UUID.randomUUID(), "APPROVED", null)).getStatus().value());
    }

    @Test
    void repeatedRejectionsRaiseSiemAlertEvent() {
        DcConfigItem item = pending();
        when(items.countByTenantIdAndSubmittedByAndStatusAndUpdatedAtAfter(eq(tenant), eq(submitter), eq("REJECTED"), any())).thenReturn(3L);
        service.decide(as(approver), item.getId(), "REJECTED", "again");
        verify(audit).record(eq(tenant), eq(approver), eq("CONFIG_REPEATED_REJECTIONS"), anyString(), anyString(), eq("ALERT"), anyMap());
    }

    @Test
    void belowThresholdNoAlert() {
        DcConfigItem item = pending();
        when(items.countByTenantIdAndSubmittedByAndStatusAndUpdatedAtAfter(any(), any(), anyString(), any())).thenReturn(1L);
        service.decide(as(approver), item.getId(), "REJECTED", "once");
        verify(audit, never()).record(any(), any(), eq("CONFIG_REPEATED_REJECTIONS"), anyString(), anyString(), anyString(), anyMap());
    }
}
