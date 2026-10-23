package com.texora.qap.repository;

import com.texora.qap.domain.QapCredentialReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QapCredentialReferenceRepository extends JpaRepository<QapCredentialReference, UUID> {

    List<QapCredentialReference> findAllByTenantId(UUID tenantId);

    Page<QapCredentialReference> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<QapCredentialReference> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QapCredentialReference> findByTenantIdAndProductId(UUID tenantId, String productId);
}
