package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.timeout.domain.ExecutionTimeoutControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface ExecutionTimeoutControlRepository extends Repository<ExecutionTimeoutControl, UUID> {

    ExecutionTimeoutControl save(ExecutionTimeoutControl control);

    Optional<ExecutionTimeoutControl> findByTenantIdAndRouteAllowlistEntryId(UUID tenantId, UUID routeAllowlistEntryId);

    Page<ExecutionTimeoutControl> findByTenantId(UUID tenantId, Pageable pageable);
}
