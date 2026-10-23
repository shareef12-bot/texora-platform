package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemIngestionSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every finder here takes tenantId explicitly (B.5) - callers must never fetch by id alone.
 * {@link #findByIdRaw(UUID)} is the one deliberate exception, reserved for the
 * fetch-then-compare-tenant pattern used to return 403 instead of 404 on cross-tenant access;
 * it must never be exposed directly to a controller.
 */
public interface SiemIngestionSourceRepository extends JpaRepository<SiemIngestionSource, UUID> {

    List<SiemIngestionSource> findAllByTenantId(UUID tenantId);

    Optional<SiemIngestionSource> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SiemIngestionSource> findByMtlsCertRefAndStatus(
            String mtlsCertRef, com.texora.secops.siem.domain.enums.SourceStatus status);

    Optional<SiemIngestionSource> findByNameAndTenantId(String name, UUID tenantId);

    default Optional<SiemIngestionSource> findByIdRaw(UUID id) {
        return findById(id);
    }
}
