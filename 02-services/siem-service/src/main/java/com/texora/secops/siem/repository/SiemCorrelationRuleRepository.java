package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiemCorrelationRuleRepository extends JpaRepository<SiemCorrelationRule, UUID> {

    List<SiemCorrelationRule> findAllByTenantId(UUID tenantId);

    Optional<SiemCorrelationRule> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Used only by RuleExecutionSandbox - correlation must run across all tenants' APPROVED
     *  rules but each rule's evaluation stays scoped to its own tenantId internally. */
    List<SiemCorrelationRule> findAllByStatus(RuleStatus status);

    List<SiemCorrelationRule> findAllByTenantIdAndStatus(UUID tenantId, RuleStatus status);

    default Optional<SiemCorrelationRule> findByIdRaw(UUID id) {
        return findById(id);
    }
}
