package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcConfigApproval;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface ConfigApprovalRepository extends Repository<DcConfigApproval, UUID> {

    <S extends DcConfigApproval> S save(S approval);

    boolean existsByTenantIdAndConfigItemIdAndApproverId(UUID tenantId, UUID configItemId, UUID approverId);
}
