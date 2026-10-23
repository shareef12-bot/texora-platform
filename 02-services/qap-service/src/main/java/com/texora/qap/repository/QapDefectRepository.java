package com.texora.qap.repository;

import com.texora.qap.domain.QapDefect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The unique constraint on (failure_signature_hash, tenant_id) enforces idempotency
 * at the database level; findByFailureSignatureHashAndTenantId is the dedupe lookup.
 */
@Repository
public interface QapDefectRepository extends JpaRepository<QapDefect, UUID> {

    List<QapDefect> findAllByTenantId(UUID tenantId);

    Page<QapDefect> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<QapDefect> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QapDefect> findByFailureSignatureHashAndTenantId(String failureSignatureHash, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
