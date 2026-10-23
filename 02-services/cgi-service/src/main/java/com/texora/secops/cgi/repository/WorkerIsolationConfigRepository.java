package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.execution.domain.IsolationConfigStatus;
import com.texora.secops.cgi.execution.domain.WorkerIsolationConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface WorkerIsolationConfigRepository extends Repository<WorkerIsolationConfig, UUID> {

    WorkerIsolationConfig save(WorkerIsolationConfig config);

    WorkerIsolationConfig saveAndFlush(WorkerIsolationConfig config);

    Optional<WorkerIsolationConfig> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkerIsolationConfig> findWithLockByIdAndTenantId(UUID id, UUID tenantId);

    Page<WorkerIsolationConfig> findByTenantId(UUID tenantId, Pageable pageable);

    List<WorkerIsolationConfig> findByTenantIdAndHandlerIdAndStatus(UUID tenantId, String handlerId, IsolationConfigStatus status);

    boolean existsByTenantIdAndHandlerIdAndStatus(UUID tenantId, String handlerId, IsolationConfigStatus status);

    @SystemWide
    boolean existsById(UUID id);
}
