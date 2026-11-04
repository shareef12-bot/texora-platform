package com.texora.secops.ldap.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Pass-through RBAC interceptor for the {@code integration-test} profile.
 *
 * <p>Reads actor and tenant UUIDs from test-injected headers so integration tests
 * can exercise tenant isolation without a real IAM token. This bean ONLY exists
 * in the {@code integration-test} Spring profile.
 *
 * <p><b>NEVER activate this profile in production or staging.</b>
 */
@Component
@Profile("integration-test")
public class IntegrationTestRbacInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String actor  = request.getHeader("X-Integration-Actor");
        String tenant = request.getHeader("X-Integration-Tenant");

        UUID actorId  = actor  != null ? UUID.fromString(actor)  : UUID.randomUUID();
        UUID tenantId = tenant != null ? UUID.fromString(tenant) : UUID.randomUUID();

        // Minimal TokenClaims-compatible stand-in stored as a request attribute
        request.setAttribute(RbacInterceptor.CLAIMS_ATTR,
                new IntegrationTestClaims(actorId, tenantId));
        return true;
    }
}
