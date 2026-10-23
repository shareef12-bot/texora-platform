package com.texora.secops.dc.security;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticates (via the shared {@link TokenValidator}) and authorizes every /api/v1 request BEFORE the handler runs.
 * Fails closed: any exception from token validation, missing claim, missing {@link RequiresRole} declaration or
 * missing role results in 401/403. No service-local credential checking is implemented here (B.6.1).
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    public static final String CALLER_ATTRIBUTE = "com.texora.secops.dc.caller";

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    private final TokenValidator tokenValidator;
    private final TenantResolver tenantResolver;
    private final DcAuditRecorder auditRecorder;

    public RbacInterceptor(TokenValidator tokenValidator, TenantResolver tenantResolver, DcAuditRecorder auditRecorder) {
        this.tokenValidator = tokenValidator;
        this.tenantResolver = tenantResolver;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TokenClaims claims = authenticate(request);
        CallerContext caller = toCaller(claims);
        tenantResolver.assertConsistentWithSharedContext(caller.getTenantId());
        request.setAttribute(CALLER_ATTRIBUTE, caller);

        if (!(handler instanceof HandlerMethod)) {
            return true; // not a controller method (e.g. unmapped path -> 404 by MVC); caller is already authenticated
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequiresRole required = handlerMethod.getMethodAnnotation(RequiresRole.class);
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        if (required == null) {
            deny(caller, endpoint, "<none declared>");
            throw DcException.forbidden("RBAC_DENIED", "Endpoint has no declared role - denied");
        }
        boolean allowed;
        try {
            allowed = claims.hasRole(required.value());
        } catch (RuntimeException e) {
            LOGGER.warn("Role check failed unexpectedly - denying", e);
            allowed = false;
        }
        if (!allowed) {
            deny(caller, endpoint, required.value());
            throw DcException.forbidden("RBAC_DENIED", "Caller lacks required role");
        }
        return true;
    }

    private TokenClaims authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.trim().isEmpty()) {
            throw DcException.unauthenticated("Missing bearer token");
        }
        try {
            TokenClaims claims = tokenValidator.validate(header);
            if (claims == null || claims.getTenantId() == null || claims.getSubject() == null) {
                throw DcException.unauthenticated("Token is missing required claims");
            }
            return claims;
        } catch (DcException e) {
            throw e;
        } catch (RuntimeException e) {
            // Includes the shared PlatformException.UnauthenticatedException; any failure => 401 (fail closed).
            LOGGER.warn("Token validation failed: {}", e.getClass().getSimpleName());
            throw DcException.unauthenticated("Invalid or expired token");
        }
    }

    private CallerContext toCaller(TokenClaims claims) {
        Set<String> roles = claims.getRoles() == null ? new HashSet<String>() : new HashSet<String>(claims.getRoles());
        return new CallerContext(claims.getTenantId(), claims.getSubject(), claims.getEmail(), roles);
    }

    /** Every policy decision is audited. Audit failure must never turn a DENY into an allow. */
    private void deny(CallerContext caller, String endpoint, String role) {
        try {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("requiredRole", role);
            auditRecorder.recordIndependent(caller.getTenantId(), caller.getSubjectId(), "RBAC_DENIED",
                    "ENDPOINT", endpoint, "DENIED", payload);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to audit RBAC denial for subject {} (request is still denied)", caller.getSubjectId(), e);
        }
    }

    /** Convenience for controllers/tests. */
    public static UUID tenantOf(CallerContext caller) {
        return caller.getTenantId();
    }
}
