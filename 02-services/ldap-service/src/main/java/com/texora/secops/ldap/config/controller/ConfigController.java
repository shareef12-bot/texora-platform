package com.texora.secops.ldap.config.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes non-sensitive runtime configuration for debugging.
 * Credentials and secrets are explicitly excluded.
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Value("${ldap.engine.host:STUB}")
    private String engineHost;

    @Value("${ldap.engine.port:0}")
    private int enginePort;

    @Value("${ldap.sync.retry-after-minutes:15}")
    private int retryAfterMinutes;

    @Value("${ldap.sync.max-retry-attempts:5}")
    private int maxRetryAttempts;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "engineHost", engineHost,
                "enginePort", enginePort,
                "syncRetryAfterMinutes", retryAfterMinutes,
                "syncMaxRetryAttempts", maxRetryAttempts,
                "stubMode", "STUB".equals(engineHost)
        ));
    }
}
