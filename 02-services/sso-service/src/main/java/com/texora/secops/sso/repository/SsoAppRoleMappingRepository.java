package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.SsoAppRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SsoAppRoleMappingRepository extends JpaRepository<SsoAppRoleMapping, UUID> {

    List<SsoAppRoleMapping> findAllByApplicationIdAndTenantId(UUID applicationId, UUID tenantId);
}
