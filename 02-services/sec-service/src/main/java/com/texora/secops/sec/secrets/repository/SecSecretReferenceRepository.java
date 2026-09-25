package com.texora.secops.sec.secrets.repository;

import com.texora.secops.sec.secrets.domain.SecSecretReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecSecretReferenceRepository extends JpaRepository<SecSecretReference, UUID> {

    Page<SecSecretReference> findByTenantIdAndOwningService(UUID tenantId, String owningService, Pageable pageable);

    Optional<SecSecretReference> findByTenantIdAndLogicalName(UUID tenantId, String logicalName);

    Optional<SecSecretReference> findByIdAndTenantId(UUID id, UUID tenantId);
}
