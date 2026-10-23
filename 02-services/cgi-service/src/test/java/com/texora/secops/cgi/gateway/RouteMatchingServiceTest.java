package com.texora.secops.cgi.gateway;

import com.texora.secops.cgi.gateway.service.RouteMatchingService;
import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteMatchingServiceTest {

    private final RouteAllowlistRepository repo = mock(RouteAllowlistRepository.class);
    private final RouteMatchingService service = new RouteMatchingService(repo);
    private final UUID tenant = UUID.randomUUID();

    private RouteAllowlistEntry entry(RouteStatus status, UUID tenantId) {
        return new RouteAllowlistEntry(UUID.randomUUID(), tenantId, "/legacy/report", "stub-echo", 5, status, UUID.randomUUID(), Instant.now(), Instant.now());
    }

    @Test
    void approvedEntryMatches() {
        when(repo.findByTenantIdAndRoutePatternAndStatus(tenant, "/legacy/report", RouteStatus.APPROVED))
                .thenReturn(Optional.of(entry(RouteStatus.APPROVED, tenant)));
        assertThat(service.match(tenant, "/legacy/report")).isPresent();
    }

    @Test
    void unlistedRouteIsEmpty() {
        when(repo.findByTenantIdAndRoutePatternAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        assertThat(service.match(tenant, "/legacy/other")).isEmpty();
    }

    @Test
    void nonApprovedOrForeignTenantRowsAreNeverReturnedEvenIfRepositoryMisbehaves() {
        when(repo.findByTenantIdAndRoutePatternAndStatus(any(), any(), any())).thenReturn(Optional.of(entry(RouteStatus.PENDING_APPROVAL, tenant)));
        assertThat(service.match(tenant, "/legacy/report")).isEmpty();
        when(repo.findByTenantIdAndRoutePatternAndStatus(any(), any(), any())).thenReturn(Optional.of(entry(RouteStatus.APPROVED, UUID.randomUUID())));
        assertThat(service.match(tenant, "/legacy/report")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "/legacy/../etc/passwd", "//legacy", "/legacy//x", "/legacy/", "/a%2e%2e/b", "/a;jsessionid=1",
            "/a b", "/a\\b", "/*", "/legacy/{id}", "legacy/report", "/a/./b"})
    void malformedOrTraversalPathsNeverReachTheRepository(String path) {
        assertThat(service.match(tenant, path)).isEmpty();
        verify(repo, never()).findByTenantIdAndRoutePatternAndStatus(any(), any(), any());
    }

    @Test
    void nullTenantOrPathIsEmpty() {
        assertThat(service.match(null, "/legacy/report")).isEmpty();
        assertThat(service.match(tenant, null)).isEmpty();
    }
}
