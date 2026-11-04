package com.texora.secops.vpn.repository;

import com.texora.secops.vpn.domain.GatewayBinding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayBindingRepository extends JpaRepository<GatewayBinding, UUID> {

    Optional<GatewayBinding> findBySessionIdAndTenantId(UUID sessionId, UUID tenantId);

    /**
     * SYSTEM lookup used only by the mTLS dataplane callback, which carries no tenant claim.
     * The opaque reference is globally unique (unique index); the tenant is taken from the row found.
     */
    Optional<GatewayBinding> findByDataplaneSessionRef(String dataplaneSessionRef);

    @Query("select b from GatewayBinding b where b.teardownState = 'PENDING' "
            + "and (b.lastTeardownAttemptAt is null or b.lastTeardownAttemptAt <= :cutoff) order by b.boundAt")
    List<GatewayBinding> findTeardownDueAcrossTenants(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GatewayBinding b set b.lastTeardownAttemptAt = :now, b.teardownAttempts = b.teardownAttempts + 1 "
            + "where b.id = :id and b.tenantId = :tenantId and b.teardownState = 'PENDING' "
            + "and (b.lastTeardownAttemptAt is null or b.lastTeardownAttemptAt <= :cutoff)")
    int claimTeardown(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                      @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    long countByTeardownState(String teardownState);
}
