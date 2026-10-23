package com.texora.secops.ldap.sync.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.security.RbacInterceptor;
import com.texora.secops.ldap.sync.service.DirectoryReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/directory")
public class SyncController {

    private final DirectoryReconciliationService reconciliationService;

    public SyncController(DirectoryReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Manually triggers a reconciliation of a single entry DN.
     * Requires {@code ldap.sync.trigger} role.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> triggerSync(@RequestParam("entryDn") String entryDn,
                                                            HttpServletRequest request) {
        if (entryDn == null || entryDn.isBlank()) {
            throw new IllegalArgumentException("entryDn must not be blank");
        }
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        reconciliationService.reconcileEntry(claims.getTenantId(), entryDn, claims.getSubject());
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "entryDn", entryDn,
                "message", "Reconciliation triggered"
        ));
    }
}
