package com.texora.secops.cgi.repository;

import com.texora.secops.cgi.route.domain.RouteApproval;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Tenant-scoped: every method takes tenantId (enforced by RepositoryTenantScopeTest). Exposes no unfiltered CRUD. */
public interface RouteApprovalRepository extends Repository<RouteApproval, UUID> {

    RouteApproval save(RouteApproval approval);

    List<RouteApproval> findByTenantIdAndRouteAllowlistEntryId(UUID tenantId, UUID routeAllowlistEntryId);
}
