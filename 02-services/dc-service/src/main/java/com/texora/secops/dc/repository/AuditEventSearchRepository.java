package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcAuditEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dynamic audit search. The tenant predicate is added unconditionally by this class - callers cannot omit it. */
@Repository
public class AuditEventSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<DcAuditEvent> search(UUID tenantId, UUID actorId, String action, Instant from, Instant to,
                                     int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DcAuditEvent> query = cb.createQuery(DcAuditEvent.class);
        Root<DcAuditEvent> root = query.from(DcAuditEvent.class);
        query.select(root).where(predicates(cb, root, tenantId, actorId, action, from, to).toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("occurredAt")));
        TypedQuery<DcAuditEvent> typed = entityManager.createQuery(query);
        typed.setFirstResult(offset);
        typed.setMaxResults(limit);
        return typed.getResultList();
    }

    public long count(UUID tenantId, UUID actorId, String action, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<DcAuditEvent> root = query.from(DcAuditEvent.class);
        query.select(cb.count(root)).where(predicates(cb, root, tenantId, actorId, action, from, to).toArray(new Predicate[0]));
        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Predicate> predicates(CriteriaBuilder cb, Root<DcAuditEvent> root, UUID tenantId, UUID actorId,
                                       String action, Instant from, Instant to) {
        List<Predicate> predicates = new ArrayList<Predicate>();
        predicates.add(cb.equal(root.get("tenantId"), tenantId));
        if (actorId != null) {
            predicates.add(cb.equal(root.get("actorId"), actorId));
        }
        if (action != null && !action.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("action"), action));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
        }
        return predicates;
    }
}
