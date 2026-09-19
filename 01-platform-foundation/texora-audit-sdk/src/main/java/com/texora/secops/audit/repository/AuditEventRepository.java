package com.texora.secops.audit.repository;

import com.texora.secops.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditEvent}.
 *
 * <p>SELECT and INSERT only at the application level — no UPDATE on content
 * columns, no DELETE (standard B.6 §4). Only {@code kafka_status} may be
 * updated by the retry mechanism.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Finds events that have not yet been successfully published to Kafka.
     * Used by the retry scheduler.
     */
    @Query("SELECT e FROM AuditEvent e WHERE e.kafkaStatus = 'PENDING' OR e.kafkaStatus = 'FAILED' ORDER BY e.occurredAt ASC")
    List<AuditEvent> findUnpublished();

    /**
     * Updates only the Kafka status column — the only permitted mutation.
     */
    @Modifying
    @Query("UPDATE AuditEvent e SET e.kafkaStatus = :status WHERE e.id = :id")
    void updateKafkaStatus(@Param("id") UUID id, @Param("status") String status);

    List<AuditEvent> findByTenantIdAndActionOrderByOccurredAtDesc(UUID tenantId, String action);
}
