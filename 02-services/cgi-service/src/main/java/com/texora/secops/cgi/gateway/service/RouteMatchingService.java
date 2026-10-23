package com.texora.secops.cgi.gateway.service;

import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.route.RoutePatterns;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Matches ONLY against APPROVED entries of the caller's tenant (CGI-F-007). Anything else yields empty, which the
 * gateway turns into 404 BEFORE any worker exists. There is no fall-through and no reduced-privilege mode.
 */
@Service
public class RouteMatchingService {

    private final RouteAllowlistRepository repository;

    public RouteMatchingService(RouteAllowlistRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<RouteAllowlistEntry> match(UUID tenantId, String requestPath) {
        if (tenantId == null || !RoutePatterns.isValid(requestPath)) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndRoutePatternAndStatus(tenantId, requestPath, RouteStatus.APPROVED)
                // belt and braces: never trust the query alone
                .filter(e -> e.getStatus() == RouteStatus.APPROVED && tenantId.equals(e.getTenantId())
                        && requestPath.equals(e.getRoutePattern()));
    }
}
