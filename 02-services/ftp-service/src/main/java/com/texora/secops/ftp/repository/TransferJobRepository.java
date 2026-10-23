package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.TransferJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TransferJobRepository extends JpaRepository<TransferJob, UUID> {

    Optional<TransferJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TransferJob> findByTenantId(UUID tenantId, Pageable pageable);

    Page<TransferJob> findByTenantIdAndStatus(UUID tenantId, JobStatus status, Pageable pageable);

    boolean existsByIdAndTenantIdAndStatusIn(UUID id, UUID tenantId, Collection<JobStatus> statuses);

    /**
     * Compare-and-set status transition. Returns the number of rows changed: 0 means the job was no longer in one of
     * the {@code from} states (e.g. cancelled or quarantined concurrently) and the caller must stop.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransferJob j set j.status = :to, j.failureReason = :reason, j.updatedAt = :now "
            + "where j.id = :id and j.tenantId = :tenantId and j.status in :from")
    int transition(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                   @Param("from") Collection<JobStatus> from, @Param("to") JobStatus to,
                   @Param("reason") String reason, @Param("now") Instant now);
}
