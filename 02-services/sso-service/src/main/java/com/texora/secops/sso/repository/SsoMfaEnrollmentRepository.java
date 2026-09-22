package com.texora.secops.sso.repository;

import com.texora.secops.sso.domain.MfaFactorStatus;
import com.texora.secops.sso.domain.SsoMfaEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SsoMfaEnrollmentRepository extends JpaRepository<SsoMfaEnrollment, UUID> {

    List<SsoMfaEnrollment> findAllByDcUserIdAndTenantIdAndStatus(UUID dcUserId, UUID tenantId,
                                                                  MfaFactorStatus status);
}
