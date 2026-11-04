package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcSyncJob;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SYSTEM-ONLY, intentionally cross-tenant claim query for the background retry sweeper (no request tenant exists).
 * Kept separate from the tenant-scoped {@link SyncJobRepository} so request-path code cannot reach it by accident.
 */
public interface SyncJobSweepRepository extends Repository<DcSyncJob, UUID> {

    <S extends DcSyncJob> S save(S job);

    @Query(value = "select * from dc.dc_sync_job where status = 'PENDING' and next_retry_at <= :now "
            + "order by next_retry_at limit :limit for update skip locked", nativeQuery = true)
    List<DcSyncJob> claimDueAcrossTenants(@Param("now") Instant now, @Param("limit") int limit);
}
