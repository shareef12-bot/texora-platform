package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapSchemaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LdapSchemaDefinition}.
 * Every query is tenant-filtered (requirement B.5).
 */
public interface SchemaDefinitionRepository extends JpaRepository<LdapSchemaDefinition, UUID> {

    List<LdapSchemaDefinition> findAllByTenantId(UUID tenantId);

    Optional<LdapSchemaDefinition> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LdapSchemaDefinition> findAllByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT s FROM LdapSchemaDefinition s WHERE s.tenantId = :tenantId AND s.objectClass = :objectClass")
    List<LdapSchemaDefinition> findByTenantIdAndObjectClass(@Param("tenantId") UUID tenantId,
                                                             @Param("objectClass") String objectClass);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
