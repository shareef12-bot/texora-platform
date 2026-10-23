package com.texora.qap.controller;

import com.texora.qap.domain.QapFlakyTestRecord;
import com.texora.qap.dto.FlakyRecordResponse;
import com.texora.qap.flaky.FlakyTestDetectionService;
import com.texora.qap.repository.QapFlakyTestRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flaky")
@Tag(name = "Flaky Tests", description = "Manage flaky test detection and quarantine")
public class FlakyTestController {

    private final FlakyTestDetectionService flakyTestDetectionService;
    private final QapFlakyTestRecordRepository flakyTestRecordRepository;

    public FlakyTestController(FlakyTestDetectionService flakyTestDetectionService,
                               QapFlakyTestRecordRepository flakyTestRecordRepository) {
        this.flakyTestDetectionService = flakyTestDetectionService;
        this.flakyTestRecordRepository = flakyTestRecordRepository;
    }

    @GetMapping
    @Operation(summary = "List flaky test records for tenant")
    public ResponseEntity<Page<FlakyRecordResponse>> listFlakyTests(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapFlakyTestRecord> page = flakyTestRecordRepository.findByTenantId(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @PatchMapping("/{testCaseId}/quarantine")
    @Operation(summary = "Manually quarantine or unquarantine a test case")
    public ResponseEntity<Void> setQuarantine(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID testCaseId,
            @RequestParam boolean quarantined) {
        flakyTestDetectionService.setQuarantine(testCaseId, tenantId, quarantined);
        return ResponseEntity.noContent().build();
    }

    private FlakyRecordResponse toResponse(QapFlakyTestRecord r) {
        FlakyRecordResponse dto = new FlakyRecordResponse();
        dto.setId(r.getId());
        dto.setTestCaseId(r.getTestCaseId());
        dto.setTenantId(r.getTenantId());
        dto.setRunCount(r.getRunCount());
        dto.setFailCount(r.getFailCount());
        dto.setFlakeRate(r.getFlakeRate());
        dto.setQuarantined(r.isQuarantined());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }
}
