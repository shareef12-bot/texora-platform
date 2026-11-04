package com.texora.secops.siem.admin.controller;

import com.texora.secops.siem.admin.service.ConfigService;
import com.texora.secops.siem.domain.SiemConfigItem;
import com.texora.secops.siem.dto.config.ConfigItemRequest;
import com.texora.secops.siem.dto.config.ConfigItemResponse;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/api/v1/config")
    @RequiresRole("siem.config.read")
    public List<ConfigItemResponse> list() {
        return configService.list(CallerContext.currentTenantId()).stream()
                .map(ConfigItemResponse::from).toList();
    }

    @PostMapping("/api/v1/config")
    @RequiresRole("siem.config.write")
    public ResponseEntity<ConfigItemResponse> upsert(@Valid @RequestBody ConfigItemRequest request) {
        TokenClaims caller = CallerContext.get();
        SiemConfigItem item = configService.upsert(caller.getTenantId(), caller.getSubject(),
                request.getKey(), request.getValue());
        return ResponseEntity.status(HttpStatus.OK).body(ConfigItemResponse.from(item));
    }
}
