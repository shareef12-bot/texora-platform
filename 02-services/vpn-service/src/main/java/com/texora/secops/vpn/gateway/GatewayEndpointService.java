package com.texora.secops.vpn.gateway;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.GatewayEndpoint;
import com.texora.secops.vpn.dto.GatewayEndpointRequest;
import com.texora.secops.vpn.dto.GatewayEndpointResponse;
import com.texora.secops.vpn.dto.GatewayIntegrationStatus;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.GatewayEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Approved dataplane gateway endpoints per tenant, plus adapter status. Endpoint config holds URLs only - never credentials. */
@Service
public class GatewayEndpointService {

    private final GatewayEndpointRepository endpoints;
    private final GatewayIntegrationAdapter adapter;
    private final VpnAuditRecorder audit;
    private final Clock clock;

    public GatewayEndpointService(GatewayEndpointRepository endpoints, GatewayIntegrationAdapter adapter,
                                  VpnAuditRecorder audit, Clock clock) {
        this.endpoints = endpoints;
        this.adapter = adapter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GatewayIntegrationStatus status(UUID tenantId) {
        GatewayIntegrationStatus status = new GatewayIntegrationStatus();
        status.setAdapter(adapter.adapterName());
        status.setStub(adapter.isStub());
        status.setDataplaneReachable(adapter.isReachable());
        List<GatewayEndpointResponse> list = new ArrayList<>();
        for (GatewayEndpoint e : endpoints.findByTenantIdOrderByCreatedAt(tenantId)) {
            list.add(toResponse(e));
        }
        status.setEndpoints(list);
        return status;
    }

    @Transactional
    public GatewayEndpointResponse create(UUID tenantId, UUID actorId, GatewayEndpointRequest request) {
        validateUrl(request.getEndpointUrl());
        GatewayEndpoint e = new GatewayEndpoint();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setName(request.getName().trim());
        e.setEndpointUrl(request.getEndpointUrl().trim());
        e.setStatus(VpnConstants.ENDPOINT_ACTIVE);
        e.setCreatedBy(actorId);
        e.setCreatedAt(clock.instant());
        endpoints.save(e);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", e.getName());
        payload.put("endpointUrl", e.getEndpointUrl());
        audit.record(tenantId, actorId, VpnConstants.ACTION_GATEWAY_ENDPOINT_CREATED, "GATEWAY_ENDPOINT",
                e.getId().toString(), VpnConstants.OUTCOME_SUCCESS, payload);
        return toResponse(e);
    }

    /** https only, no embedded credentials, no query/fragment (those are where secrets leak into config). */
    static void validateUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw VpnException.unprocessable("GATEWAY_URL_INVALID", "Gateway endpoint must be an absolute https URL");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw VpnException.unprocessable("GATEWAY_URL_INVALID",
                        "Gateway endpoint must not contain credentials, a query string or a fragment");
            }
        } catch (URISyntaxException e) {
            throw VpnException.unprocessable("GATEWAY_URL_INVALID", "Gateway endpoint is not a valid URL");
        }
    }

    private GatewayEndpointResponse toResponse(GatewayEndpoint e) {
        GatewayEndpointResponse r = new GatewayEndpointResponse();
        r.setId(e.getId());
        r.setName(e.getName());
        r.setEndpointUrl(e.getEndpointUrl());
        r.setStatus(e.getStatus());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }
}
