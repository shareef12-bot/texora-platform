package com.texora.secops.siem.audit;

import com.texora.secops.siem.domain.SiemAuditEvent;
import com.texora.secops.siem.dto.audit.AuditEventResponse;
import com.texora.secops.siem.dto.common.PageResponse;
import com.texora.secops.siem.repository.SiemAuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditSearchService {

    private final SiemAuditEventRepository auditEventRepository;

    public AuditSearchService(SiemAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public PageResponse<AuditEventResponse> search(UUID tenantId, String action, int page, int pageSize) {
        Page<SiemAuditEvent> result = action == null || action.isBlank()
                ? auditEventRepository.findAllByTenantId(tenantId, PageRequest.of(page, pageSize))
                : auditEventRepository.findAllByTenantIdAndAction(tenantId, action, PageRequest.of(page, pageSize));

        var items = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(items, page, pageSize, result.getTotalElements());
    }

    private AuditEventResponse toResponse(SiemAuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getActorId(), event.getAction(),
                event.getTargetType(), event.getTargetId(), event.getOutcome(), event.getOccurredAt());
    }
}
