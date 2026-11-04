package com.texora.qap.repository;

import com.texora.qap.domain.QapTestRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QapTestRunRepository extends JpaRepository<QapTestRun, UUID> {

    List<QapTestRun> findAllByTenantId(UUID tenantId);

    Page<QapTestRun> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<QapTestRun> findByIdAndTenantId(UUID id, UUID tenantId);

    List<QapTestRun> findAllBySuiteIdAndTenantId(UUID suiteId, UUID tenantId);
}
