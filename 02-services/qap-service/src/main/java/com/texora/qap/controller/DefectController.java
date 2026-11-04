package com.texora.qap.controller;

import com.texora.qap.domain.QapDefect;
import com.texora.qap.dto.DefectResponse;
import com.texora.qap.repository.QapDefectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/defects")
@Tag(name = "Defects", description = "Query deduplicated defect tickets")
public class DefectController {

    private final QapDefectRepository defectRepository;

    public DefectController(QapDefectRepository defectRepository) {
        this.defectRepository = defectRepository;
    }

    @GetMapping
    @Operation(summary = "List defects for tenant")
    public ResponseEntity<Page<DefectResponse>> listDefects(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Pageable pageable) {
        Page<QapDefect> page = defectRepository.findByTenantId(tenantId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @GetMapping("/{defectId}")
    @Operation(summary = "Get a specific defect")
    public ResponseEntity<DefectResponse> getDefect(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID defectId) {
        QapDefect defect = defectRepository.findByIdAndTenantId(defectId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Defect not found"));
        return ResponseEntity.ok(toResponse(defect));
    }

    private DefectResponse toResponse(QapDefect d) {
        DefectResponse r = new DefectResponse();
        r.setId(d.getId());
        r.setTenantId(d.getTenantId());
        r.setTestCaseId(d.getTestCaseId());
        r.setFailureSignatureHash(d.getFailureSignatureHash());
        r.setIssueTrackerRef(d.getIssueTrackerRef());
        r.setOccurrenceCount(d.getOccurrenceCount());
        r.setFirstSeenAt(d.getFirstSeenAt());
        r.setLastSeenAt(d.getLastSeenAt());
        return r;
    }
}
