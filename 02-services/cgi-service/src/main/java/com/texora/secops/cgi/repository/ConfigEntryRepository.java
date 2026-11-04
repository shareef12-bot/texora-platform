package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.config.domain.ConfigEntry;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface ConfigEntryRepository extends Repository<ConfigEntry, UUID> {

    ConfigEntry save(ConfigEntry entry);

    Optional<ConfigEntry> findByTenantIdAndConfigKey(UUID tenantId, String configKey);

    List<ConfigEntry> findByTenantId(UUID tenantId);
}
