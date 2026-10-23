package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.AuditOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditOutboxRepository extends JpaRepository<AuditOutbox, UUID> {

    @Query("select o from AuditOutbox o where o.publishedAt is null and o.nextAttemptAt <= :now order by o.createdAt")
    List<AuditOutbox> findDue(@Param("now") Instant now, Pageable pageable);

    /** Atomic claim across replicas; the lease expires on its own if the claimer dies. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuditOutbox o set o.nextAttemptAt = :leaseUntil where o.id = :id and o.publishedAt is null and o.nextAttemptAt <= :now")
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuditOutbox o set o.publishedAt = :now, o.lastError = null where o.id = :id")
    int markPublished(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuditOutbox o set o.attempts = o.attempts + 1, o.nextAttemptAt = :next, o.lastError = :error where o.id = :id")
    int markFailed(@Param("id") UUID id, @Param("next") Instant next, @Param("error") String error);

    long countByPublishedAtIsNull();
}
