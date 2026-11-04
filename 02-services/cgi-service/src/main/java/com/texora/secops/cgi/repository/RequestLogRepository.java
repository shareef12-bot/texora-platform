package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.execution.domain.RequestLog;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface RequestLogRepository extends Repository<RequestLog, UUID> {

    RequestLog save(RequestLog log);

    Optional<RequestLog> findByIdAndTenantId(UUID id, UUID tenantId);
}
