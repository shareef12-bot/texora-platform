package com.texora.secops.sec.config;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecConfigRepository extends JpaRepository<SecConfigEntry, UUID> {
    Page<SecConfigEntry> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<SecConfigEntry> findByTenantIdAndConfigKey(UUID tenantId, String configKey);
}
