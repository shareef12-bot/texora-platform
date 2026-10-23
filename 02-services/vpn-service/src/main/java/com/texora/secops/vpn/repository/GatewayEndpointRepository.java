package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.GatewayEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GatewayEndpointRepository extends JpaRepository<GatewayEndpoint, UUID> {

    List<GatewayEndpoint> findByTenantIdOrderByCreatedAt(UUID tenantId);

    List<GatewayEndpoint> findByTenantIdAndStatusOrderByCreatedAt(UUID tenantId, String status);
}
