package com.texora.secops.sec.secrets.repository;

import com.texora.secops.sec.secrets.domain.SecKeyRotationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SecKeyRotationRecordRepository extends JpaRepository<SecKeyRotationRecord, UUID> {

    Page<SecKeyRotationRecord> findByTenantIdOrderByRotationDueAtDesc(UUID tenantId, Pageable pageable);

    List<SecKeyRotationRecord> findByRotatedAtIsNullAndRotationDueAtBefore(Instant now);
}
