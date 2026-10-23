package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiemAlertRepository extends JpaRepository<SiemAlert, UUID> {

    List<SiemAlert> findAllByTenantId(UUID tenantId);

    Optional<SiemAlert> findByIdAndTenantId(UUID id, UUID tenantId);

    default Optional<SiemAlert> findByIdRaw(UUID id) {
        return findById(id);
    }
}
