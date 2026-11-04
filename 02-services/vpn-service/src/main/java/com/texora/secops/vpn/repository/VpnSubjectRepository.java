package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.VpnSubject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VpnSubjectRepository extends JpaRepository<VpnSubject, UUID> {

    Optional<VpnSubject> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<VpnSubject> findByTenantIdAndDcUserId(UUID tenantId, UUID dcUserId);

    boolean existsByIdAndTenantIdNot(UUID id, UUID tenantId);

    Page<VpnSubject> findByTenantId(UUID tenantId, Pageable pageable);
}
