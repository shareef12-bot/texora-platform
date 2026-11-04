package com.texora.qap.repository;

import com.texora.qap.domain.QapFlakyTestRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QapFlakyTestRecordRepository extends JpaRepository<QapFlakyTestRecord, UUID> {

    Optional<QapFlakyTestRecord> findByTestCaseIdAndTenantId(UUID testCaseId, UUID tenantId);

    List<QapFlakyTestRecord> findAllByTenantId(UUID tenantId);

    Page<QapFlakyTestRecord> findByTenantId(UUID tenantId, Pageable pageable);

    List<QapFlakyTestRecord> findAllByTenantIdAndQuarantinedTrue(UUID tenantId);
}
