package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface RouteAllowlistRepository extends Repository<RouteAllowlistEntry, UUID> {

    RouteAllowlistEntry save(RouteAllowlistEntry entry);

    RouteAllowlistEntry saveAndFlush(RouteAllowlistEntry entry);

    Optional<RouteAllowlistEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Row lock for the approval decision so two approvers cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RouteAllowlistEntry> findWithLockByIdAndTenantId(UUID id, UUID tenantId);

    Page<RouteAllowlistEntry> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<RouteAllowlistEntry> findByTenantIdAndRoutePatternAndStatus(UUID tenantId, String routePattern, RouteStatus status);

    boolean existsByTenantIdAndRoutePatternAndStatusIn(UUID tenantId, String routePattern, Collection<RouteStatus> statuses);

    /** @see SystemWide - only used to turn a missing tenant-scoped row into 403 (other tenant) instead of 404. */
    @SystemWide
    boolean existsById(UUID id);
}
