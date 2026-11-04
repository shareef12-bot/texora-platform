package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.ChecksumRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChecksumRecordRepository extends JpaRepository<ChecksumRecord, UUID> {

    Optional<ChecksumRecord> findByTransferJobIdAndAlgorithmAndTenantId(UUID transferJobId, String algorithm, UUID tenantId);

    Page<ChecksumRecord> findByTenantId(UUID tenantId, Pageable pageable);

    Page<ChecksumRecord> findByTenantIdAndTransferJobId(UUID tenantId, UUID transferJobId, Pageable pageable);

    /**
     * Race-free idempotent insert backed by the UNIQUE (transfer_job_id, algorithm) constraint (Requirement 4).
     * Returns 1 if this call created the record, 0 if one already existed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO ftp.ftp_checksum_record "
            + "(id, tenant_id, transfer_job_id, algorithm, expected_value, computed_value, verified, recorded_at) "
            + "VALUES (:id, :tenantId, :jobId, :algorithm, :expected, :computed, :verified, :recordedAt) "
            + "ON CONFLICT (transfer_job_id, algorithm) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("jobId") UUID jobId,
                       @Param("algorithm") String algorithm, @Param("expected") String expected,
                       @Param("computed") String computed, @Param("verified") boolean verified,
                       @Param("recordedAt") Instant recordedAt);
}
