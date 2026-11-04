package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemIngestionLog;
import com.texora.secops.siem.domain.enums.IngestionLogStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SiemIngestionLogRepository extends JpaRepository<SiemIngestionLog, UUID> {

    List<SiemIngestionLog> findAllByTenantId(UUID tenantId);

    List<SiemIngestionLog> findAllByStatusAndNextRetryAtLessThanEqual(
            IngestionLogStatus status, Instant now);

    long countByStatus(IngestionLogStatus status);

    @Query("select l from SiemIngestionLog l where l.tenantId = :tenantId and l.status = :status")
    List<SiemIngestionLog> findByTenantAndStatus(
            @Param("tenantId") UUID tenantId, @Param("status") IngestionLogStatus status);
}
