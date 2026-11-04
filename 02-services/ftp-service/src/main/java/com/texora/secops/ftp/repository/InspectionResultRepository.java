package com.texora.secops.ftp.repository;

import com.texora.secops.ftp.domain.InspectionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, UUID> {

    List<InspectionResult> findByTransferJobIdAndTenantIdOrderByInspectedAtAsc(UUID transferJobId, UUID tenantId);
}
