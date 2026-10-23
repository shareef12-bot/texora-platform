package com.texora.secops.ldap.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-endpoint RBAC enforcement for the LDAP service.
 * Executes before every REST handler.
 *
 * <p>Uses the real {@link TokenValidator} from {@code texora-iam-client} — never
 * an invented class (see KNOWN-INTEGRATION-PITFALLS §1).
 *
 * <p>Fail-closed: any missing token, invalid token, or missing role results in
 * 401 or 403; exceptions from the validator are always propagated.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    /**
     * Request attribute key where validated {@link TokenClaims} are stored
     * so downstream handlers can read them without re-validating.
     */
    public static final String CLAIMS_ATTR = "ldap.tokenClaims";

    private final TokenValidator tokenValidator;
    private final EndpointRoleRegistry roleRegistry;

    public RbacInterceptor(TokenValidator tokenValidator, EndpointRoleRegistry roleRegistry) {
        this.tokenValidator = tokenValidator;
        this.roleRegistry = roleRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Actuator health/metrics — no auth required (internal-network-only paths)
        if (path.startsWith("/actuator/")) {
            return true;
        }

        // Validate token — fail closed on any error
        TokenClaims claims;
        try {
            claims = tokenValidator.validate(request.getHeader("Authorization"));
        } catch (PlatformException.UnauthenticatedException e) {
            LOGGER.warn("LDAP RBAC: unauthenticated request to {} {}", method, path);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                      "Valid bearer token required");
            return false;
        }

        // Store claims for downstream use (e.g. audit, tenant resolution)
        request.setAttribute(CLAIMS_ATTR, claims);

        // Determine required role for this endpoint
        String requiredRole = roleRegistry.requiredRole(method, path);
        if (requiredRole == null) {
            // No role mapping — deny by default (fail closed)
            LOGGER.warn("LDAP RBAC: no role mapping for {} {} — denying", method, path);
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "RBAC_NO_MAPPING",
                      "No role mapping configured for this endpoint");
            return false;
        }

        if (!claims.hasRole(requiredRole)) {
            LOGGER.warn("LDAP RBAC: subject={} lacks role '{}' for {} {}",
                        claims.getSubject(), requiredRole, method, path);
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "RBAC_DENIED",
                      "Caller lacks required role: " + requiredRole);
            return false;
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String code, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"status\":" + status + ",\"error\":\"" +
                (status == 401 ? "Unauthorized" : "Forbidden") +
                "\",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"
            );
        } catch (Exception ex) {
            LOGGER.error("Failed to write RBAC error response", ex);
        }
    }
}
