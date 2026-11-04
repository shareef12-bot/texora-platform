package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiemConfigItemRepository extends JpaRepository<SiemConfigItem, UUID> {

    List<SiemConfigItem> findAllByTenantId(UUID tenantId);

    Optional<SiemConfigItem> findByTenantIdAndConfigKey(UUID tenantId, String configKey);
}
