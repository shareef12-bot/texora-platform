package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.TransferAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferAttemptRepository extends JpaRepository<TransferAttempt, UUID> {

    Optional<TransferAttempt> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TransferAttempt> findByTransferJobIdAndTenantIdOrderByAttemptNumberAsc(UUID transferJobId, UUID tenantId);

    @Query("select coalesce(max(a.attemptNumber), 0) from TransferAttempt a "
            + "where a.transferJobId = :jobId and a.tenantId = :tenantId")
    int maxAttemptNumber(@Param("jobId") UUID jobId, @Param("tenantId") UUID tenantId);
}
