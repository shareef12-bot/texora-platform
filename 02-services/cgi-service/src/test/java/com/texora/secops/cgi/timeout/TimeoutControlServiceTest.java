package com.texora.secops.cgi.timeout;

import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.repository.ExecutionTimeoutControlRepository;
import com.texora.secops.cgi.repository.RouteAllowlistRepository;
import com.texora.secops.cgi.route.domain.RouteAllowlistEntry;
import com.texora.secops.cgi.route.domain.RouteStatus;
import com.texora.secops.cgi.security.CallerContext;
import com.texora.secops.cgi.timeout.domain.ExecutionTimeoutControl;
import com.texora.secops.cgi.timeout.dto.TimeoutControlRequest;
import com.texora.secops.cgi.timeout.dto.TimeoutControlResponse;
import com.texora.secops.cgi.timeout.service.TimeoutControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeoutControlServiceTest {

    private final ExecutionTimeoutControlRepository controls = mock(ExecutionTimeoutControlRepository.class);
    private final RouteAllowlistRepository routes = mock(RouteAllowlistRepository.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private final ExecutionProperties props = new ExecutionProperties();
    private TimeoutControlService service;
    private final UUID tenant = UUID.randomUUID();
    private final CallerContext caller = new CallerContext(tenant, UUID.randomUUID(), Set.of());
    private RouteAllowlistEntry route;

    @BeforeEach
    void setUp() {
        service = new TimeoutControlService(controls, routes, props, audit, Clock.systemUTC());
        route = new RouteAllowlistEntry(UUID.randomUUID(), tenant, "/legacy/report", "stub-echo", 10, RouteStatus.APPROVED,
                UUID.randomUUID(), Instant.now(), Instant.now());
        when(routes.findByIdAndTenantId(route.getId(), tenant)).thenReturn(Optional.of(route));
    }

    @Test
    void effectiveTimeoutUsesRouteValueWhenNoOverride() {
        when(controls.findByTenantIdAndRouteAllowlistEntryId(tenant, route.getId())).thenReturn(Optional.empty());
        assertThat(service.effectiveTimeout(tenant, route)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void overrideWinsButIsCappedByCeiling() {
        ExecutionTimeoutControl c = new ExecutionTimeoutControl(UUID.randomUUID(), tenant, route.getId(), 99999, UUID.randomUUID(), Instant.now());
        when(controls.findByTenantIdAndRouteAllowlistEntryId(tenant, route.getId())).thenReturn(Optional.of(c));
        assertThat(service.effectiveTimeout(tenant, route)).isEqualTo(Duration.ofSeconds(props.getMaxTimeoutCeilingSeconds()));
    }

    @Test
    void upsertCreatesAuditedControl() {
        when(controls.findByTenantIdAndRouteAllowlistEntryId(tenant, route.getId())).thenReturn(Optional.empty());
        TimeoutControlResponse r = service.upsert(caller, new TimeoutControlRequest(route.getId(), 7));
        assertThat(r.getMaxExecutionSeconds()).isEqualTo(7);
        verify(controls).save(any());
        verify(audit).recordInTransaction(any());
    }

    @Test
    void upsertRejectsOutOfRangeNonApprovedAndForeignRoutes() {
        assertThatThrownBy(() -> service.upsert(caller, new TimeoutControlRequest(route.getId(), 0))).isInstanceOf(CgiApiException.class);
        assertThatThrownBy(() -> service.upsert(caller, new TimeoutControlRequest(route.getId(), 100000))).isInstanceOf(CgiApiException.class);
        route.setStatus(RouteStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.upsert(caller, new TimeoutControlRequest(route.getId(), 5))).isInstanceOf(CgiApiException.class);
        UUID foreign = UUID.randomUUID();
        when(routes.findByIdAndTenantId(foreign, tenant)).thenReturn(Optional.empty());
        when(routes.existsById(foreign)).thenReturn(true);
        assertThatThrownBy(() -> service.upsert(caller, new TimeoutControlRequest(foreign, 5)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        when(routes.existsById(foreign)).thenReturn(false);
        assertThatThrownBy(() -> service.upsert(caller, new TimeoutControlRequest(foreign, 5)))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    }

    @Test
    void listIsTenantScoped() {
        when(controls.findByTenantId(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(service.list(tenant, 0, 10).getItems()).isEmpty();
    }
}
