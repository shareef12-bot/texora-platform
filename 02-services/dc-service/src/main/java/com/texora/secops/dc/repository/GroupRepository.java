package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends Repository<DcGroup, UUID> {

    <S extends DcGroup> S save(S group);

    Optional<DcGroup> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<DcGroup> findByTenantIdAndName(UUID tenantId, String name);

    Page<DcGroup> findByTenantId(UUID tenantId, Pageable pageable);

    List<DcGroup> findByTenantId(UUID tenantId);
}
