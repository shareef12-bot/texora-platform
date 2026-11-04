package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.QuarantineRecord;
import com.texora.secops.ftp.domain.QuarantineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuarantineRecordRepository extends JpaRepository<QuarantineRecord, UUID> {

    Optional<QuarantineRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QuarantineRecord> findByTransferJobIdAndTenantId(UUID transferJobId, UUID tenantId);

    Page<QuarantineRecord> findByTenantId(UUID tenantId, Pageable pageable);

    Page<QuarantineRecord> findByTenantIdAndStatus(UUID tenantId, QuarantineStatus status, Pageable pageable);
}
