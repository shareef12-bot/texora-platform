package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Deliberately NOT a JpaRepository: only save (insert) and read operations exist, so no code path can even express
 * an update or delete of an audit row. The database trigger (V2) enforces the same rule underneath.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    AuditEvent save(AuditEvent event);
}
