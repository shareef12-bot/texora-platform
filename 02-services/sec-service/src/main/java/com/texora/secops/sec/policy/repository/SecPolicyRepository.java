package com.texora.secops.sec.policy.repository;

import com.texora.secops.sec.policy.domain.SecPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecPolicyRepository extends JpaRepository<SecPolicy, UUID> {

    // Always tenant-filtered per B.5
    Page<SecPolicy> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<SecPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<SecPolicy> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
