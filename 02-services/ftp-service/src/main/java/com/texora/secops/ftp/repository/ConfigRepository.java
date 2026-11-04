package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigRepository extends JpaRepository<ConfigEntry, UUID> {

    List<ConfigEntry> findByTenantId(UUID tenantId);

    Optional<ConfigEntry> findByTenantIdAndConfigKey(UUID tenantId, String configKey);
}
