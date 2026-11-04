package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcPolicy;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends Repository<DcPolicy, UUID> {

    <S extends DcPolicy> S save(S policy);

    Optional<DcPolicy> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DcPolicy> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
