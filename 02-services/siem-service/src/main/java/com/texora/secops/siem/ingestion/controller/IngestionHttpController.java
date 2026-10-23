package com.texora.secops.siem.ingestion.controller;

import com.texora.secops.siem.ingestion.adapter.MultiProtocolIngestionAdapter;
import com.texora.secops.siem.ingestion.adapter.MultiProtocolIngestionAdapter.HttpIngestOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The raw HTTP ingestion path (LLD's "HTTP" protocol). Deliberately outside SSO/RBAC
 * (see SecurityConfig, WebMvcConfig) - sources authenticate via mTLS, not bearer tokens
 * (B.6.1). This controller is intentionally as thin as the admin controllers (B.3): it only
 * extracts the transport-level identity the mTLS terminator has already verified and hands
 * everything else to {@link MultiProtocolIngestionAdapter}, which is the only class that
 * interprets it.
 */
@RestController
public class IngestionHttpController {

    private final MultiProtocolIngestionAdapter ingestionAdapter;

    public IngestionHttpController(MultiProtocolIngestionAdapter ingestionAdapter) {
        this.ingestionAdapter = ingestionAdapter;
    }

    @PostMapping("/api/v1/ingest/http/{sourceKey}")
    public ResponseEntity<Void> ingest(@PathVariable String sourceKey,
                                        @RequestHeader(value = "X-Client-Cert-CN", required = false) String clientCertCn,
                                        @RequestBody String rawBody,
                                        HttpServletRequest request) {
        String presentedCn = clientCertCn != null ? clientCertCn : extractCnFromServletCert(request);
        HttpIngestOutcome outcome = ingestionAdapter.acceptHttpEvent(sourceKey, presentedCn, rawBody);

        return switch (outcome) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case REJECTED_UNAUTHENTICATED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case REJECTED_INACTIVE_SOURCE -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        };
    }

    private String extractCnFromServletCert(HttpServletRequest request) {
        Object certs = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs instanceof java.security.cert.X509Certificate[] chain && chain.length > 0) {
            return chain[0].getSubjectX500Principal().getName();
        }
        return null;
    }
}
