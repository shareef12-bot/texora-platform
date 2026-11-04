package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapSchemaApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LdapSchemaApproval}.
 * Every query is tenant-filtered (requirement B.5).
 */
public interface SchemaApprovalRepository extends JpaRepository<LdapSchemaApproval, UUID> {

    List<LdapSchemaApproval> findAllBySchemaDefinitionIdAndTenantId(UUID schemaDefinitionId, UUID tenantId);

    Optional<LdapSchemaApproval> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsBySchemaDefinitionIdAndApproverIdAndTenantId(UUID schemaDefinitionId, UUID approverId, UUID tenantId);
}
