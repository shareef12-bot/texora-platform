package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapBindConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LdapBindConfig}.
 * Every query is tenant-filtered (requirement B.5).
 */
public interface BindConfigRepository extends JpaRepository<LdapBindConfig, UUID> {

    List<LdapBindConfig> findAllByTenantId(UUID tenantId);

    Optional<LdapBindConfig> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<LdapBindConfig> findByBindDnAndTenantId(String bindDn, UUID tenantId);

    List<LdapBindConfig> findAllByTenantIdAndStatus(UUID tenantId, String status);
}
