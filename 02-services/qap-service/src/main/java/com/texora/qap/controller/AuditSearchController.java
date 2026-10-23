package com.texora.qap.controller;

import com.texora.qap.domain.QapAuditEvent;
import com.texora.qap.dto.AuditEventResponse;
import com.texora.qap.repository.QapAuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Search append-only QAP audit events")
public class AuditSearchController {

    private final QapAuditEventRepository auditEventRepository;

    public AuditSearchController(QapAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    @Operation(summary = "List audit events for tenant (paginated)")
    public ResponseEntity<Page<AuditEventResponse>> listEvents(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapAuditEvent> page = auditEventRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    private AuditEventResponse toResponse(QapAuditEvent e) {
        AuditEventResponse dto = new AuditEventResponse();
        dto.setId(e.getId());
        dto.setTenantId(e.getTenantId());
        dto.setEventType(e.getAction());           // mapped from action
        dto.setActorId(e.getActorId() != null ? e.getActorId().toString() : null);
        dto.setResourceType(e.getTargetType());    // mapped from targetType
        dto.setResourceId(e.getTargetId());        // mapped from targetId
        dto.setPayload(e.getPayload());
        dto.setOccurredAt(e.getOccurredAt());
        return dto;
    }
}
