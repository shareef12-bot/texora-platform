package com.texora.secops.sec.audit.repository;

import com.texora.secops.sec.audit.domain.SecAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecAuditEventRepository extends JpaRepository<SecAuditEvent, UUID> {

    Page<SecAuditEvent> findByTenantIdOrderBySequenceNoDesc(UUID tenantId, Pageable pageable);

    // Fetch events for chain verification in ascending sequence order
    @Query("SELECT e FROM SecAuditEvent e ORDER BY e.sequenceNo ASC")
    Iterable<SecAuditEvent> findAllInSequenceOrder();

    // Fetch the last event for chain linking (get its hash to use as prev_hash)
    @Query("SELECT e FROM SecAuditEvent e ORDER BY e.sequenceNo DESC LIMIT 1")
    Optional<SecAuditEvent> findLatestEvent();

    // Count events per tenant for metrics
    long countByTenantId(UUID tenantId);

    // Find event by sequence for chain repair/verification
    Optional<SecAuditEvent> findBySequenceNo(Long sequenceNo);

    @Query("SELECT MAX(e.sequenceNo) FROM SecAuditEvent e")
    Optional<Long> findMaxSequenceNo();
}
