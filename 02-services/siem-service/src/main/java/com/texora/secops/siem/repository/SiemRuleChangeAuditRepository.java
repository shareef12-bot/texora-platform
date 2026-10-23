package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemRuleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiemRuleChangeAuditRepository extends JpaRepository<SiemRuleChangeAudit, UUID> {

    List<SiemRuleChangeAudit> findAllByCorrelationRuleId(UUID correlationRuleId);

    List<SiemRuleChangeAudit> findAllByTenantId(UUID tenantId);
}
