package com.texora.secops.vpn.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.domain.AuditEvent;
import com.texora.secops.vpn.dto.AuditEventResponse;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.repository.VpnAuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditSearchService {

    private static final int MAX_PAGE_SIZE = 200;

    private final VpnAuditEventRepository events;
    private final ObjectMapper objectMapper;

    public AuditSearchService(VpnAuditEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(UUID tenantId, Instant from, Instant to, String action, UUID actorId,
                                                   String targetType, String targetId, int page, int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId)); // ALWAYS tenant-filtered
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null && !targetId.isBlank()) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AuditEvent> result = events.findAll(spec,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        List<AuditEventResponse> items = new ArrayList<>();
        for (AuditEvent e : result.getContent()) {
            items.add(toResponse(e));
        }
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        AuditEventResponse r = new AuditEventResponse();
        r.setId(e.getId());
        r.setActorId(e.getActorId());
        r.setAction(e.getAction());
        r.setTargetType(e.getTargetType());
        r.setTargetId(e.getTargetId());
        r.setOutcome(e.getOutcome());
        r.setOccurredAt(e.getOccurredAt());
        try {
            r.setPayload(objectMapper.readValue(e.getPayload(), new TypeReference<LinkedHashMap<String, Object>>() { }));
        } catch (Exception ex) {
            r.setPayload(Map.of("payloadError", "unreadable"));
        }
        return r;
    }
}
