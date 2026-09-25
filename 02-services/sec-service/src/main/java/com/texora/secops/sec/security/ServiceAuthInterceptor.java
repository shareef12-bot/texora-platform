package com.texora.secops.sec.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates service-to-service calls via mTLS client certificates.
 * Applied to the /api/v1/policies/evaluate and /api/v1/secret-references/{name}/resolve
 * endpoints.
 *
 * <p>The client certificate CN identifies the calling service. The CN must be in
 * the list of trusted services for the platform.</p>
 *
 * <p>This interceptor populates TenantContext from the certificate's tenant
 * extension (OID or SAN) rather than from an SSO token.</p>
 */
@Component
public class ServiceAuthInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAuthInterceptor.class);

    // Standard attribute name for client certs in servlet containers
    private static final String CERT_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    @Value("${sec.mtls.enabled:true}")
    private boolean mtlsEnabled;

    @Value("#{'${sec.mtls.trusted-services:sso,dc,ldap,ftp,cgi,siem,vpn}'.split(',')}")
    private List<String> trustedServices;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!mtlsEnabled) {
            // Disabled for local development; fail open is acceptable in dev only
            LOGGER.warn("mTLS is DISABLED — service auth bypassed. DO NOT use in production.");
            setFallbackTenant();
            return true;
        }

        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(CERT_ATTRIBUTE);

        if (certs == null || certs.length == 0) {
            LOGGER.warn("mTLS: no client certificate presented for request to {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "mTLS client certificate required for service-to-service calls");
            return false;
        }

        X509Certificate clientCert = certs[0];
        String cn = extractCN(clientCert.getSubjectX500Principal().getName());

        if (cn == null || !trustedServices.contains(cn)) {
            LOGGER.warn("mTLS: untrusted service CN='{}' for request to {}", cn, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Service '" + cn + "' is not a trusted platform service");
            return false;
        }

        // Extract tenant from the certificate (convention: OU=<tenant-uuid>)
        String tenantFromCert = extractOU(clientCert.getSubjectX500Principal().getName());
        if (tenantFromCert != null) {
            try {
                TenantContext.setTenantId(UUID.fromString(tenantFromCert));
            } catch (IllegalArgumentException ex) {
                LOGGER.debug("OU in mTLS cert is not a tenant UUID: {}", tenantFromCert);
            }
        }

        // Store calling service name for owner-scope checks in SecretReferenceService
        request.setAttribute("callerService", cn);
        LOGGER.debug("mTLS auth: caller={} uri={}", cn, request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractCN(String dn) {
        return Arrays.stream(dn.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith("CN="))
                .map(part -> part.substring(3))
                .findFirst()
                .orElse(null);
    }

    private String extractOU(String dn) {
        return Arrays.stream(dn.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith("OU="))
                .map(part -> part.substring(3))
                .findFirst()
                .orElse(null);
    }

    private void setFallbackTenant() {
        // Dev-only fallback — system tenant
        TenantContext.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
