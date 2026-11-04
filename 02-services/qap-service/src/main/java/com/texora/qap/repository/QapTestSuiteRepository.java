package com.texora.qap.repository;

import com.texora.qap.domain.QapTestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every query is filtered by tenant_id — Shared Standards B.5.
 */
@Repository
public interface QapTestSuiteRepository extends JpaRepository<QapTestSuite, UUID> {

    List<QapTestSuite> findAllByTenantId(UUID tenantId);

    Page<QapTestSuite> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<QapTestSuite> findByIdAndTenantId(UUID id, UUID tenantId);
}
