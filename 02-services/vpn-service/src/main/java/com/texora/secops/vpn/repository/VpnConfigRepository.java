package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.VpnConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VpnConfigRepository extends JpaRepository<VpnConfigEntry, UUID> {

    List<VpnConfigEntry> findByTenantIdOrderByConfigKey(UUID tenantId);

    Optional<VpnConfigEntry> findByTenantIdAndConfigKey(UUID tenantId, String configKey);
}
