package com.texora.qap.repository;

import com.texora.qap.domain.QapTestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QapTestCaseRepository extends JpaRepository<QapTestCase, UUID> {

    List<QapTestCase> findAllBySuiteId(UUID suiteId);

    Page<QapTestCase> findBySuiteId(UUID suiteId, Pageable pageable);

    Optional<QapTestCase> findByIdAndSuiteId(UUID id, UUID suiteId);
}
