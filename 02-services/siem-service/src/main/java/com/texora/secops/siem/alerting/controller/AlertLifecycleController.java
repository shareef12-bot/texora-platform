package com.texora.secops.siem.alerting.controller;

import com.texora.secops.siem.alerting.service.AlertLifecycleService;
import com.texora.secops.siem.domain.SiemAlert;
import com.texora.secops.siem.domain.SiemAlertTransition;
import com.texora.secops.siem.dto.alert.AlertResponse;
import com.texora.secops.siem.dto.alert.AlertTransitionRequest;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Thin controller (B.3): maps DTO &lt;-&gt; service calls only. */
@RestController
public class AlertLifecycleController {

    private final AlertLifecycleService alertLifecycleService;

    public AlertLifecycleController(AlertLifecycleService alertLifecycleService) {
        this.alertLifecycleService = alertLifecycleService;
    }

    @GetMapping("/api/v1/alert-lifecycle")
    @RequiresRole("siem.alert.read")
    public List<AlertResponse> list() {
        return alertLifecycleService.list(CallerContext.currentTenantId()).stream()
                .map(AlertResponse::from).toList();
    }

    @GetMapping("/api/v1/alert-lifecycle/{id}")
    @RequiresRole("siem.alert.read")
    public AlertResponse get(@PathVariable UUID id) {
        SiemAlert alert = alertLifecycleService.get(id, CallerContext.currentTenantId());
        return AlertResponse.from(alert);
    }

    @GetMapping("/api/v1/alert-lifecycle/{id}/history")
    @RequiresRole("siem.alert.read")
    public List<SiemAlertTransition> history(@PathVariable UUID id) {
        return alertLifecycleService.history(id, CallerContext.currentTenantId());
    }

    @PostMapping("/api/v1/alert-lifecycle/{id}/transition")
    @RequiresRole("siem.alert.write")
    public ResponseEntity<AlertResponse> transition(@PathVariable UUID id,
                                                      @Valid @RequestBody AlertTransitionRequest request) {
        SiemAlert alert = alertLifecycleService.transition(id, request.getToStatus(), request.getNotes());
        return ResponseEntity.status(HttpStatus.OK).body(AlertResponse.from(alert));
    }
}
