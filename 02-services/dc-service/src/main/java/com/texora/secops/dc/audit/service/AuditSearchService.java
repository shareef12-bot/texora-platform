package com.texora.secops.dc.audit.service;

import com.texora.secops.dc.domain.DcAuditEvent;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.repository.AuditEventSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /api/v1/audit - always scoped to the caller's tenant (the search repository adds the predicate itself). */
@Service
public class AuditSearchService {

    private final AuditEventSearchRepository repository;

    public AuditSearchService(AuditEventSearchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<DcAuditEvent> search(UUID tenantId, UUID actorId, String action, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw DcException.unprocessable("INVALID_RANGE", "'from' must not be after 'to'");
        }
        List<DcAuditEvent> items = repository.search(tenantId, actorId, action, from, to,
                (int) pageable.getOffset(), pageable.getPageSize());
        long total = repository.count(tenantId, actorId, action, from, to);
        return new PageImpl<DcAuditEvent>(items, pageable, total);
    }
}
