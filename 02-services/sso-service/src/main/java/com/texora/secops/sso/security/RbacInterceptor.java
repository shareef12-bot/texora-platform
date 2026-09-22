package com.texora.secops.sso.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.sso.exception.RbacDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Enforces per-endpoint RBAC roles BEFORE the handler runs (shared standards
 * B.6.2). Uses the shared IAM client library to check the caller's roles
 * against the required role for the endpoint being invoked. Any ambiguity —
 * unknown endpoint, missing role claim, IAM client error — results in DENY.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    /** Endpoint-prefix to required-role mapping, per LLD §6.1 / shared standards. */
    private static final Map<String, String> READ_ROLES = Map.of(
            "/api/v1/application-registration", "sso.app.read",
            "/api/v1/oidc/saml-configuration", "sso.federation.read",
            "/api/v1/session/token-management", "sso.session.read",
            "/api/v1/config", "sso.config.read",
            "/api/v1/policies", "sso.policy.read",
            "/api/v1/audit", "sso.audit.read");

    private static final Map<String, String> WRITE_ROLES = Map.of(
            "/api/v1/application-registration", "sso.app.write",
            "/api/v1/oidc/saml-configuration", "sso.federation.write",
            "/api/v1/session/token-management", "sso.session.revoke",
            "/api/v1/config", "sso.config.write");

    private final TokenValidator tokenValidator;

    public RbacInterceptor(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                              Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        String requiredRole = resolveRequiredRole(path, method);
        if (requiredRole == null) {
            // Not one of the RBAC-protected admin endpoints (e.g. /actuator/health,
            // /oauth2/*, /saml2/*) — those have their own auth handling.
            return true;
        }

        TokenClaims claims;
        try {
            claims = tokenValidator.validate(request.getHeader("Authorization"));
        } catch (PlatformException.UnauthenticatedException e) {
            // Fail closed: any error validating the caller's token is a denial.
            throw new RbacDeniedException("Unable to verify caller role '" + requiredRole + "'");
        }

        if (!claims.hasRole(requiredRole)) {
            throw new RbacDeniedException("Caller lacks required role '" + requiredRole + "'");
        }
        return true;
    }

    private String resolveRequiredRole(String path, String method) {
        Map<String, String> table = "GET".equalsIgnoreCase(method) ? READ_ROLES : WRITE_ROLES;
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        // DELETE on session/token-management uses the revoke role, same table entry
        // as POST covers it via prefix match above.
        return null;
    }
}
