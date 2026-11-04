package com.texora.qap.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces RBAC via the SEC policy evaluation endpoint before every handler.
 * Fail-closed: denied or error → 403.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    private final PolicyDecisionClient policyDecisionClient;

    public RbacInterceptor(PolicyDecisionClient policyDecisionClient) {
        this.policyDecisionClient = policyDecisionClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String principal = auth.getName();
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            LOGGER.warn("RBAC: missing X-Tenant-ID header for principal={}", principal);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return false;
        }

        String resource = "qap:" + deriveResource(request.getRequestURI());
        String action = mapMethod(request.getMethod());

        boolean allowed = policyDecisionClient.evaluate(principal, resource, action, tenantId);
        if (!allowed) {
            LOGGER.warn("RBAC DENIED principal={} resource={} action={} tenantId={}",
                    principal, resource, action, tenantId);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        return true;
    }

    private String deriveResource(String uri) {
        // Strip leading /api/v1/ prefix, take first path segment as resource name
        String stripped = uri.replaceFirst("^/api/v1/", "");
        int slash = stripped.indexOf('/');
        return slash > 0 ? stripped.substring(0, slash) : stripped;
    }

    private String mapMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET"    -> "read";
            case "POST"   -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default       -> method.toLowerCase();
        };
    }
}
