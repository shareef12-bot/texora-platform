package com.texora.secops.sso.application.controller;

import com.texora.secops.sso.application.service.ApplicationRegistrationService;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.dto.ApplicationRegistrationRequest;
import com.texora.secops.sso.dto.ApplicationRegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * GET/POST /api/v1/application-registration — roles sso.app.read / sso.app.write,
 * enforced by RbacInterceptor before this handler ever runs. No business
 * logic here (shared standards B.3) — this only maps DTO &lt;-&gt; service.
 */
@RestController
@RequestMapping("/api/v1/application-registration")
public class ApplicationRegistrationController {

    private final ApplicationRegistrationService applicationRegistrationService;

    public ApplicationRegistrationController(ApplicationRegistrationService applicationRegistrationService) {
        this.applicationRegistrationService = applicationRegistrationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationRegistrationResponse> register(
            @Valid @RequestBody ApplicationRegistrationRequest request, Principal principal) {
        UUID registeredBy = UUID.fromString(principal.getName());
        SsoApplication application = applicationRegistrationService.register(request, registeredBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(application));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationRegistrationResponse>> list() {
        List<ApplicationRegistrationResponse> body = applicationRegistrationService.list().stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationRegistrationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(applicationRegistrationService.get(id)));
    }

    private ApplicationRegistrationResponse toResponse(SsoApplication app) {
        return new ApplicationRegistrationResponse(app.getId(), app.getClientId(), app.getName(),
                app.getProductId(), app.getStatus(), app.getRegisteredBy(), app.getRegisteredAt());
    }
}
