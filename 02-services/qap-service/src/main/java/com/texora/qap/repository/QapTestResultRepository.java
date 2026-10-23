package com.texora.qap.repository;

import com.texora.qap.domain.QapTestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QapTestResultRepository extends JpaRepository<QapTestResult, UUID> {

    List<QapTestResult> findAllByTestRunId(UUID testRunId);

    Page<QapTestResult> findByTestRunIdAndTenantId(UUID testRunId, UUID tenantId, Pageable pageable);

    List<QapTestResult> findAllByTestCaseId(UUID testCaseId);
}
