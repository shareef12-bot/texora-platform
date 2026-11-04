package com.texora.secops.ftp.security;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.exception.FtpUnauthenticatedException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Validates the SSO-issued bearer token (via the shared TokenValidator - FTP performs NO credential checks of its
 * own) and enforces per-endpoint roles BEFORE any handler executes.
 *
 * Fail closed: unknown handler type, missing @RequiresRole, missing/invalid token, unexpected validator error,
 * missing role -> request denied. The tenant comes from the token claim only, never from request parameters.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    private final TokenValidator tokenValidator;
    private final FtpAuditTrail auditTrail;

    public RbacInterceptor(TokenValidator tokenValidator, FtpAuditTrail auditTrail) {
        this.tokenValidator = tokenValidator;
        this.auditTrail = auditTrail;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            LOGGER.warn("Denying {} {}: non-controller handler on a guarded path", request.getMethod(), request.getRequestURI());
            throw new RbacDeniedException("RBAC_NO_POLICY", "No access policy for this resource");
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequiresRole required = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresRole.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresRole.class);
        }
        if (required == null || required.value().length == 0) {
            LOGGER.error("Denying {}: handler {} declares no @RequiresRole", request.getRequestURI(), handlerMethod);
            throw new RbacDeniedException("RBAC_NO_POLICY", "No access policy for this resource");
        }

        TokenClaims claims = authenticate(request);

        boolean permitted = false;
        for (String role : required.value()) {
            if (claims.hasRole(role)) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            auditDenial(claims, request, required.value());
            throw new RbacDeniedException("Caller lacks required role");
        }

        CallerAuthentication caller = CallerAuthentication.of(claims);
        SecurityContextHolder.getContext().setAuthentication(caller);
        TenantBinder.bind(caller.getTenantId());
        return true;
    }

    private TokenClaims authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new FtpUnauthenticatedException("Missing bearer token");
        }
        try {
            TokenClaims claims = tokenValidator.validate(header);
            if (claims == null || claims.getTenantId() == null || claims.getSubject() == null) {
                throw new FtpUnauthenticatedException("Token carries no tenant/subject");
            }
            return claims;
        } catch (FtpUnauthenticatedException e) {
            throw e;
        } catch (PlatformException.UnauthenticatedException e) {
            throw new FtpUnauthenticatedException("Invalid or expired token");
        } catch (RuntimeException e) {
            // Anything unexpected from validation is NEVER treated as success.
            LOGGER.error("Unexpected error validating token - denying", e);
            throw new FtpUnauthenticatedException("Token could not be validated");
        }
    }

    private void auditDenial(TokenClaims claims, HttpServletRequest request, String[] roles) {
        try {
            auditTrail.rbacDenied(claims.getTenantId(), claims.getSubject().toString(),
                    Map.<String, Object>of("method", request.getMethod(), "path", request.getRequestURI(),
                            "requiredAnyOf", String.join(",", roles)));
        } catch (RuntimeException e) {
            // The denial still stands even if the audit write fails.
            LOGGER.error("Failed to audit RBAC denial", e);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContextHolder.clearContext();
        TenantBinder.clear();
    }
}
