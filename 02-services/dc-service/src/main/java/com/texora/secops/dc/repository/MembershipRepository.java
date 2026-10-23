package com.texora.secops.dc.repository;

import com.texora.secops.dc.domain.DcGroupMembership;
import com.texora.secops.dc.domain.DcGroupMembershipId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MembershipRepository extends Repository<DcGroupMembership, DcGroupMembershipId> {

    <S extends DcGroupMembership> S save(S membership);

    @Query("select count(m) > 0 from DcGroupMembership m where m.tenantId = :tenantId and m.id.groupId = :groupId and m.id.userId = :userId")
    boolean existsMembership(@Param("tenantId") UUID tenantId, @Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("select m from DcGroupMembership m where m.tenantId = :tenantId and m.id.userId = :userId")
    List<DcGroupMembership> findByUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query("select m from DcGroupMembership m where m.tenantId = :tenantId")
    List<DcGroupMembership> findAllByTenant(@Param("tenantId") UUID tenantId);
}
