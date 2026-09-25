package com.texora.secops.sec.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Resolves the tenant from the SSO JWT and populates TenantContext.
 * Applied to all SSO-authenticated (human/admin) endpoints.
 *
 * <p>Per-endpoint role enforcement is handled by @PreAuthorize annotations
 * on each controller method (B.3, B.6 §2).</p>
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            // Extract tenant_id from the JWT claim — NEVER from request params (B.5)
            String tenantClaim = jwt.getClaimAsString("tenant_id");
            if (tenantClaim != null) {
                try {
                    TenantContext.setTenantId(UUID.fromString(tenantClaim));
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Invalid tenant_id claim in JWT: {}", tenantClaim);
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
