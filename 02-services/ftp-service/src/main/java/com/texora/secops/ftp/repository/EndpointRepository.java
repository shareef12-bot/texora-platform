package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.FtpEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Every query method takes tenantId (Shared Standards B.5). */
public interface EndpointRepository extends JpaRepository<FtpEndpoint, UUID> {

    Optional<FtpEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<FtpEndpoint> findByTenantId(UUID tenantId, Pageable pageable);
}
