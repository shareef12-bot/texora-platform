package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/** Insert + search only. The service never updates or deletes audit rows (and the DB refuses to). */
public interface VpnAuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
