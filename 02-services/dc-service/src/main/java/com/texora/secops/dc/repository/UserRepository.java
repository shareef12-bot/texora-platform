package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends Repository<DcUser, UUID> {

    <S extends DcUser> S save(S user);

    Optional<DcUser> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<DcUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<DcUser> findByTenantIdAndHrmsEmployeeId(UUID tenantId, String hrmsEmployeeId);

    Page<DcUser> findByTenantId(UUID tenantId, Pageable pageable);

    List<DcUser> findByTenantId(UUID tenantId);
}
