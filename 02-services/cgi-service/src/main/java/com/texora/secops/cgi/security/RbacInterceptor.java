package com.texora.secops.cgi.security;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticates EVERY request (shared IAM client, no local credential check) and enforces per-endpoint roles
 * BEFORE the handler runs. FAIL CLOSED: a missing/invalid token, a validator fault, a handler without an access
 * annotation, or any other doubt results in a rejection - never in a pass-through.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    private final TokenValidator tokenValidator;
    private final CgiAuditWriter auditWriter;

    public RbacInterceptor(TokenValidator tokenValidator, CgiAuditWriter auditWriter) {
        this.tokenValidator = tokenValidator;
        this.auditWriter = auditWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw CgiApiException.unauthenticated();
        }

        TokenClaims claims;
        try {
            claims = tokenValidator.validate(authorization);
        } catch (PlatformException.UnauthenticatedException e) {
            throw CgiApiException.unauthenticated();
        } catch (RuntimeException e) {
            // Log the class only: nothing derived from the Authorization header may reach the logs.
            LOGGER.error("Token validation failed unexpectedly ({}) - failing closed", e.getClass().getName());
            throw CgiApiException.unauthenticated();
        }
        if (claims == null || claims.getTenantId() == null || claims.getSubject() == null) {
            throw CgiApiException.unauthenticated();
        }

        CallerContext caller = CallerContext.from(claims);
        request.setAttribute(CallerContext.REQUEST_ATTRIBUTE, caller);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            LOGGER.error("Non-controller handler reached the RBAC interceptor - denying");
            audit(caller, request, "RBAC_NOT_CONFIGURED");
            throw CgiApiException.rbacNotConfigured();
        }

        RequiresRole requiresRole = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresRole.class);
        if (requiresRole == null) {
            requiresRole = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresRole.class);
        }
        if (requiresRole != null) {
            for (String role : requiresRole.value()) {
                if (!caller.hasRole(role)) {
                    audit(caller, request, "RBAC_DENIED");
                    throw CgiApiException.rbacDenied();
                }
            }
            return true;
        }

        boolean authenticatedOnly = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), AuthenticatedOnly.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), AuthenticatedOnly.class);
        if (authenticatedOnly) {
            return true;
        }

        LOGGER.error("Handler {} has no @RequiresRole/@AuthenticatedOnly - denying (fail closed)", handlerMethod.getShortLogMessage());
        audit(caller, request, "RBAC_NOT_CONFIGURED");
        throw CgiApiException.rbacNotConfigured();
    }

    private void audit(CallerContext caller, HttpServletRequest request, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        payload.put("method", request.getMethod());
        // The URI is NOT recorded: it can carry caller-controlled values. Only the matched API family is.
        payload.put("area", request.getRequestURI() != null && request.getRequestURI().startsWith("/gateway") ? "gateway" : "admin");
        auditWriter.recordNeverFail(AuditRecord.of(caller.getTenantId(), caller.getSubject(), "RBAC_DECISION", "ENDPOINT",
                "n/a", CgiAuditWriter.OUTCOME_DENIED, payload));
    }
}
