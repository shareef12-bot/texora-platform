package com.texora.secops.siem.ingestion.controller;

import com.texora.secops.siem.domain.SiemIngestionSource;
import com.texora.secops.siem.domain.enums.SourceStatus;
import com.texora.secops.siem.dto.ingestion.IngestionSourceRequest;
import com.texora.secops.siem.dto.ingestion.IngestionSourceResponse;
import com.texora.secops.siem.ingestion.service.IngestionSourceService;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin CRUD for registered ingestion channels (B.3: thin, no business logic). Distinct from
 *  the raw event-receipt path exposed by IngestionHttpController / the Kafka listener. */
@RestController
public class IngestionSourceController {

    private final IngestionSourceService ingestionSourceService;

    public IngestionSourceController(IngestionSourceService ingestionSourceService) {
        this.ingestionSourceService = ingestionSourceService;
    }

    @GetMapping("/api/v1/log-ingestion")
    @RequiresRole("siem.ingestion.read")
    public List<IngestionSourceResponse> list() {
        return ingestionSourceService.list(CallerContext.currentTenantId()).stream()
                .map(IngestionSourceResponse::from).toList();
    }

    @GetMapping("/api/v1/log-ingestion/{id}")
    @RequiresRole("siem.ingestion.read")
    public IngestionSourceResponse get(@PathVariable UUID id) {
        return IngestionSourceResponse.from(ingestionSourceService.get(id, CallerContext.currentTenantId()));
    }

    @PostMapping("/api/v1/log-ingestion")
    @RequiresRole("siem.ingestion.write")
    public ResponseEntity<IngestionSourceResponse> register(@Valid @RequestBody IngestionSourceRequest request) {
        TokenClaims caller = CallerContext.get();
        SiemIngestionSource source = ingestionSourceService.register(caller.getTenantId(), caller.getSubject(),
                request.getName(), request.getProtocol(), request.getMtlsCertRef(), request.getProductId());
        return ResponseEntity.status(HttpStatus.CREATED).body(IngestionSourceResponse.from(source));
    }

    @PutMapping("/api/v1/log-ingestion/{id}/status")
    @RequiresRole("siem.ingestion.write")
    public IngestionSourceResponse setStatus(@PathVariable UUID id, @RequestParam SourceStatus status) {
        SiemIngestionSource source = ingestionSourceService.setStatus(id, CallerContext.currentTenantId(), status);
        return IngestionSourceResponse.from(source);
    }
}
