package com.texora.qap.controller;

import com.texora.qap.domain.QapTestCase;
import com.texora.qap.domain.QapTestSuite;
import com.texora.qap.dto.*;
import com.texora.qap.suite.service.TestSuiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suites")
@Tag(name = "Test Suites", description = "Manage QAP test suites and test cases")
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    public TestSuiteController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @PostMapping
    @Operation(summary = "Create a new test suite")
    public ResponseEntity<SuiteResponse> createSuite(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody CreateSuiteRequest request) {
        QapTestSuite suite = testSuiteService.createSuite(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(suite));
    }

    @GetMapping
    @Operation(summary = "List test suites for tenant")
    public ResponseEntity<Page<SuiteResponse>> listSuites(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapTestSuite> page = testSuiteService.listSuites(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @GetMapping("/{suiteId}")
    @Operation(summary = "Get test suite by ID")
    public ResponseEntity<SuiteResponse> getSuite(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID suiteId) {
        QapTestSuite suite = testSuiteService.getSuite(suiteId, tenantId);
        return ResponseEntity.ok(toResponse(suite));
    }

    @DeleteMapping("/{suiteId}")
    @Operation(summary = "Delete a test suite")
    public ResponseEntity<Void> deleteSuite(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID suiteId) {
        testSuiteService.deleteSuite(suiteId, tenantId);
        return ResponseEntity.noContent().build();
    }

    // ---- Test Cases ----

    @PostMapping("/{suiteId}/cases")
    @Operation(summary = "Add a test case to a suite")
    public ResponseEntity<TestCaseResponse> addTestCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID suiteId,
            @Valid @RequestBody CreateTestCaseRequest request) {
        QapTestCase tc = testSuiteService.addTestCase(suiteId, tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCaseResponse(tc));
    }

    @GetMapping("/{suiteId}/cases")
    @Operation(summary = "List test cases in a suite")
    public ResponseEntity<Page<TestCaseResponse>> listTestCases(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID suiteId,
            Pageable pageable) {
        Page<QapTestCase> page = testSuiteService.listTestCases(suiteId, tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toCaseResponse));
    }

    @DeleteMapping("/{suiteId}/cases/{caseId}")
    @Operation(summary = "Remove a test case from a suite")
    public ResponseEntity<Void> deleteTestCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId) {
        testSuiteService.deleteTestCase(suiteId, caseId, tenantId);
        return ResponseEntity.noContent().build();
    }

    // ---- Mappers ----

    private SuiteResponse toResponse(QapTestSuite s) {
        SuiteResponse r = new SuiteResponse();
        r.setId(s.getId());
        r.setTenantId(s.getTenantId());
        r.setName(s.getName());
        r.setTargetSystem(s.getTargetSystem());
        r.setSuiteType(s.getSuiteType());
        r.setStatus(s.getStatus());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    private TestCaseResponse toCaseResponse(QapTestCase tc) {
        TestCaseResponse r = new TestCaseResponse();
        r.setId(tc.getId());
        r.setSuiteId(tc.getSuiteId());
        r.setTenantId(tc.getTenantId());
        r.setName(tc.getName());
        r.setSteps(tc.getSteps());
        r.setExpectedResult(tc.getExpectedResult());
        r.setStatus(tc.getStatus());
        r.setCreatedAt(tc.getCreatedAt());
        return r;
    }
}
