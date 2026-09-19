package com.texora.secops.sso.audit.controller;

import com.texora.secops.sso.domain.SsoAuditEvent;
import com.texora.secops.sso.dto.AuditEventResponse;
import com.texora.secops.sso.repository.SsoAuditEventRepository;
import com.texora.secops.sso.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/audit — role sso.audit.read. Read-only search over the append-only audit trail. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditSearchController {

    private final SsoAuditEventRepository auditEventRepository;

    public AuditSearchController(SsoAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    public ResponseEntity<Page<AuditEventResponse>> search(
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var tenantId = TenantContext.get();
        Page<SsoAuditEvent> results = targetType != null
                ? auditEventRepository.findAllByTenantIdAndTargetType(tenantId, targetType, PageRequest.of(page, size))
                : auditEventRepository.findAllByTenantId(tenantId, PageRequest.of(page, size));

        return ResponseEntity.ok(results.map(this::toResponse));
    }

    private AuditEventResponse toResponse(SsoAuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getActorId(), event.getAction(),
                event.getTargetType(), event.getTargetId(), event.getOutcome(), event.getOccurredAt());
    }
}
