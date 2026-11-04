package com.texora.secops.siem.admin.service;

import com.texora.secops.siem.audit.Audited;
import com.texora.secops.siem.domain.SiemConfigItem;
import com.texora.secops.siem.repository.SiemConfigItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Backs GET/POST /api/v1/config (SIEM-F-004), mirroring the generic tenant-scoped key/value
 *  configuration pattern used by the other services in this program. */
@Service
public class ConfigService {

    private final SiemConfigItemRepository configItemRepository;

    public ConfigService(SiemConfigItemRepository configItemRepository) {
        this.configItemRepository = configItemRepository;
    }

    public List<SiemConfigItem> list(UUID tenantId) {
        return configItemRepository.findAllByTenantId(tenantId);
    }

    @Audited(action = "config.upsert", targetType = "SiemConfigItem")
    @Transactional
    public SiemConfigItem upsert(UUID tenantId, UUID actorId, String key, String value) {
        SiemConfigItem item = configItemRepository.findByTenantIdAndConfigKey(tenantId, key)
                .orElseGet(() -> new SiemConfigItem(UUID.randomUUID(), tenantId, key, value, actorId, Instant.now()));
        item.setConfigValue(value);
        item.setUpdatedBy(actorId);
        item.setUpdatedAt(Instant.now());
        return configItemRepository.save(item);
    }
}
