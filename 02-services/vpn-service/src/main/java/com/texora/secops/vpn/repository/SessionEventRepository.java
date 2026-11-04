package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionEventRepository extends JpaRepository<SessionEvent, UUID> {

    List<SessionEvent> findBySessionIdAndTenantIdOrderByOccurredAt(UUID sessionId, UUID tenantId);
}
