package com.texora.secops.sso.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guarantees TenantContext never leaks across requests on a pooled servlet
 * thread. Registered on the default (login / /oauth2/*) filter chain,
 * where — unlike the /api/v1/** chain — there is no TenantResolvingFilter
 * to own that cleanup: tenant is instead set MID-REQUEST by
 * LoginOrchestrationService itself (derived from the target application,
 * since /login runs before any bearer token exists). This filter wraps the
 * whole downstream chain (including the login controller and the
 * @Audited/AOP-wrapped service call) so the tenant set during login()
 * remains readable for the rest of that same request, and is only ever
 * cleared once the full request completes.
 */
public class TenantContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
