package com.texora.secops.ftp.audit.service;

import com.texora.secops.ftp.domain.AuditEvent;
import com.texora.secops.ftp.dto.AuditEventResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.Paging;
import com.texora.secops.ftp.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only search over the append-only audit table. Always constrained to the caller's tenant. */
@Service
public class AuditSearchService {

    private final AuditEventRepository repository;

    public AuditSearchService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(UUID tenantId, String actorId, String action, String targetType,
                                                   UUID targetId, Instant from, Instant to, int page, int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (actorId != null && !actorId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (targetType != null && !targetType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), to));
        }
        Page<AuditEvent> result = repository.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new PageResponse<>(result.getContent().stream().map(AuditSearchService::toResponse)
                .collect(Collectors.toList()), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActorId(), e.getAction(), e.getTargetType(), e.getTargetId(),
                e.getOutcome(), e.getOccurredAt(), e.getPayload());
    }
}
