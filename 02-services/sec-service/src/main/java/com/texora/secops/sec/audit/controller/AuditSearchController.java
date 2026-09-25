package com.texora.secops.sec.audit.controller;

import com.texora.secops.sec.audit.domain.SecAuditEvent;
import com.texora.secops.sec.audit.repository.SecAuditEventRepository;
import com.texora.secops.sec.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET /api/v1/audit — requires sec.audit.read
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditSearchController {

    private final SecAuditEventRepository auditEventRepository;

    public AuditSearchController(SecAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sec.audit.read')")
    public ResponseEntity<Page<SecAuditEvent>> searchAuditEvents(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(
                auditEventRepository.findByTenantIdOrderBySequenceNoDesc(tenantId, pageable));
    }
}
