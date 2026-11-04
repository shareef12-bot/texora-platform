package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LdapAttributeDefinition}.
 * Every query is tenant-filtered (requirement B.5).
 */
public interface AttributeDefinitionRepository extends JpaRepository<LdapAttributeDefinition, UUID> {

    List<LdapAttributeDefinition> findAllBySchemaDefinitionIdAndTenantId(UUID schemaDefinitionId, UUID tenantId);

    Optional<LdapAttributeDefinition> findByIdAndTenantId(UUID id, UUID tenantId);

    void deleteBySchemaDefinitionIdAndTenantId(UUID schemaDefinitionId, UUID tenantId);
}
