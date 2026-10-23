package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcConfigItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConfigItemRepository extends Repository<DcConfigItem, UUID> {

    <S extends DcConfigItem> S save(S item);

    Optional<DcConfigItem> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Row lock so two approvers cannot both decide the same item concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DcConfigItem c where c.tenantId = :tenantId and c.id = :id")
    Optional<DcConfigItem> findByIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    Page<DcConfigItem> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DcConfigItem> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    long countByTenantIdAndSubmittedByAndStatusAndUpdatedAtAfter(UUID tenantId, UUID submittedBy, String status, Instant after);
}
