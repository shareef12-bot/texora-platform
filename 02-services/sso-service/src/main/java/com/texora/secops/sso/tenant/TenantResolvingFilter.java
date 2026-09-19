package com.texora.secops.sso.tenant;

import com.texora.secops.iam.TokenClaims;
import com.texora.secops.iam.TokenValidationException;
import com.texora.secops.iam.IamTokenClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves tenant_id strictly from the validated bearer token's claims
 * (never from a request parameter or header) and binds it to
 * {@link TenantContext} for the lifetime of the request.
 *
 * The shared IAM client library (texora-iam-client) is the only component
 * trusted to validate the token; this filter just reads the already-verified
 * claims off the security context that RbacInterceptor / resource-server
 * filters populate upstream.
 */
public class TenantResolvingFilter extends OncePerRequestFilter {

    private final IamTokenClient iamTokenClient;

    public TenantResolvingFilter(IamTokenClient iamTokenClient) {
        this.iamTokenClient = iamTokenClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            TokenClaims claims = iamTokenClient.currentClaims(SecurityContextHolder.getContext());
            if (claims != null && claims.tenantId() != null) {
                TenantContext.set(claims.tenantId());
            }
            filterChain.doFilter(request, response);
        } catch (TokenValidationException e) {
            // Fail closed: ambiguous/missing tenant claim is a 401, never a
            // silently-unfiltered query.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } finally {
            TenantContext.clear();
        }
    }
}
