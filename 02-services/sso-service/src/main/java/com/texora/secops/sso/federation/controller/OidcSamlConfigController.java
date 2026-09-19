package com.texora.secops.sso.federation.controller;

import com.texora.secops.sso.domain.SsoOidcSamlConfig;
import com.texora.secops.sso.dto.OidcSamlConfigRequest;
import com.texora.secops.sso.dto.OidcSamlConfigResponse;
import com.texora.secops.sso.federation.service.IdentityProviderAdapterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** GET/POST /api/v1/oidc/saml-configuration — roles sso.federation.read / .write. */
@RestController
@RequestMapping("/api/v1/oidc/saml-configuration")
public class OidcSamlConfigController {

    private final IdentityProviderAdapterService identityProviderAdapterService;

    public OidcSamlConfigController(IdentityProviderAdapterService identityProviderAdapterService) {
        this.identityProviderAdapterService = identityProviderAdapterService;
    }

    @PostMapping
    public ResponseEntity<OidcSamlConfigResponse> create(@Valid @RequestBody OidcSamlConfigRequest request) {
        SsoOidcSamlConfig config = identityProviderAdapterService.upsertConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(config));
    }

    @GetMapping
    public ResponseEntity<List<OidcSamlConfigResponse>> list(@RequestParam UUID applicationId) {
        List<OidcSamlConfigResponse> body = identityProviderAdapterService.listForApplication(applicationId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OidcSamlConfigResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(identityProviderAdapterService.get(id)));
    }

    private OidcSamlConfigResponse toResponse(SsoOidcSamlConfig config) {
        return new OidcSamlConfigResponse(config.getId(), config.getApplicationId(), config.getProtocol(),
                config.getRedirectUris(), config.getSigningCertRef());
    }
}
