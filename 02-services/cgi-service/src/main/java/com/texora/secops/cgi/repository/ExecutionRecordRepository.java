package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.execution.domain.ExecutionRecord;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface ExecutionRecordRepository extends Repository<ExecutionRecord, UUID> {

    ExecutionRecord save(ExecutionRecord record);

    Optional<ExecutionRecord> findByTenantIdAndRequestLogId(UUID tenantId, UUID requestLogId);
}
