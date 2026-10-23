package com.texora.secops.cgi.audit.service;

import com.texora.secops.cgi.audit.domain.AuditEvent;
import com.texora.secops.cgi.audit.dto.AuditEventResponse;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.repository.AuditEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Read-only, tenant-scoped audit search. Rows were redacted at write time. */
@Service
public class AuditSearchService {

    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final AuditEventRepository repository;

    public AuditSearchService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(UUID tenantId, String action, String outcome, Instant from, Instant to,
                                                   int page, int size) {
        Pageable pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(repository.searchByTenantId(tenantId,
                action == null ? "" : action.trim(), outcome == null ? "" : outcome.trim().toUpperCase(),
                from == null ? EPOCH : from, to == null ? FAR_FUTURE : to, pageable), AuditSearchService::toResponse);
    }

    static AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActorId(), e.getAction(), e.getTargetType(), e.getTargetId(),
                e.getOutcome(), e.getOccurredAt(), e.getPayload());
    }
}
