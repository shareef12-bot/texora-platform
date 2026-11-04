package com.texora.secops.ldap.repository;

import com.texora.secops.ldap.domain.LdapSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link LdapSyncState}.
 * Every query is tenant-filtered (requirement B.5).
 */
public interface SyncStateRepository extends JpaRepository<LdapSyncState, LdapSyncState.SyncStateId> {

    @Query("SELECT s FROM LdapSyncState s WHERE s.entryDn = :entryDn AND s.tenantId = :tenantId")
    Optional<LdapSyncState> findByEntryDnAndTenantId(@Param("entryDn") String entryDn,
                                                      @Param("tenantId") UUID tenantId);

    List<LdapSyncState> findAllByTenantId(UUID tenantId);

    List<LdapSyncState> findAllByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT s FROM LdapSyncState s WHERE s.tenantId = :tenantId AND s.status IN ('PENDING', 'FAILED') ORDER BY s.retryCount ASC")
    List<LdapSyncState> findPendingOrFailedByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Finds entries eligible for the retry scheduler: status=FAILED,
     * last attempt before {@code retryBefore}, and below the max retry cap.
     */
    @Query("""
            SELECT s FROM LdapSyncState s
            WHERE s.status = :status
              AND s.lastSyncedAt < :retryBefore
              AND s.retryCount < :maxRetries
            ORDER BY s.lastSyncedAt ASC
            """)
    List<LdapSyncState> findRetryableEntries(@Param("status") String status,
                                              @Param("retryBefore") java.time.Instant retryBefore,
                                              @Param("maxRetries") int maxRetries);
}
