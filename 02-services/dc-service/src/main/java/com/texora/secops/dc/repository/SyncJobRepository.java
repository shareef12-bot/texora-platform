package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcSyncJob;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepository extends Repository<DcSyncJob, UUID> {

    <S extends DcSyncJob> S save(S job);

    Optional<DcSyncJob> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<DcSyncJob> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<DcSyncJob> findFirstByTenantIdAndSourceAndStatusOrderByStartedAtDesc(UUID tenantId, String source, String status);
}
