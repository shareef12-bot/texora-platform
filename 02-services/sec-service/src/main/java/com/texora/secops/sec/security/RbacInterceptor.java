package com.texora.secops.sec.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.domain.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the caller's identity and tenant from the SSO bearer token and populates the
 * shared {@link TenantContext}. Applied to all SSO-authenticated (human/admin) endpoints.
 *
 * <p>Token validation is delegated to the shared IAM client library
 * ({@link TokenValidator#validate(String)}); SEC performs no credential checks of its own.
 * {@code validate} fails closed: it throws
 * {@code PlatformException.UnauthenticatedException} on ANY failure (missing, expired,
 * malformed, missing claim, invalid UUID). That exception is deliberately NOT caught here;
 * it propagates to {@code secGlobalExceptionHandler}, which maps it to 401. A request
 * therefore never reaches a handler with a partially-established identity.</p>
 *
 * <p>The validated {@link TokenClaims} are exposed to controllers via the
 * {@link #CLAIMS_ATTRIBUTE} request attribute so that controllers never parse the token
 * (or a Spring {@code Jwt}) themselves.</p>
 *
 * <p>Per-endpoint role enforcement is handled by {@code @PreAuthorize} annotations on each
 * controller method (B.3, B.6 section 2).</p>
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    /** Request attribute under which the validated {@link TokenClaims} are published. */
    public static final String CLAIMS_ATTRIBUTE = "sec.tokenClaims";

    private final TokenValidator tokenValidator;

    public RbacInterceptor(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Throws PlatformException.UnauthenticatedException on any failure -> 401 (fail closed).
        // Tenant comes from the token claim, NEVER from a request parameter (B.5).
        TokenClaims claims = tokenValidator.validate(request.getHeader("Authorization"));

        TenantContext.set(claims.getTenantId());
        request.setAttribute(CLAIMS_ATTRIBUTE, claims);

        LOGGER.debug("Token validated: subject={} tenant={}", claims.getSubject(), claims.getTenantId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
