package com.texora.secops.iam.interceptor;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.domain.TenantContext;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * Spring MVC interceptor that:
 * <ol>
 *   <li>Extracts and validates the {@code Authorization: Bearer} token</li>
 *   <li>Sets {@link TenantContext} from the token's {@code ten} claim</li>
 *   <li>Enforces {@link RequiresRole} annotations — caller must hold at least
 *       one required role, or the request is rejected with 403 RBAC_DENIED</li>
 *   <li>Clears {@link TenantContext} in {@link #afterCompletion} (always)</li>
 * </ol>
 *
 * <p><strong>Fail-closed:</strong> any ambiguity, missing token, failed
 * validation, or missing role results in denial. There is no path through
 * this interceptor that silently permits access (standard B.6 §3).
 *
 * <p>Registered via {@link com.texora.secops.iam.config.IamAutoConfiguration}.
 * Services must NOT register their own auth interceptors — add roles to the
 * annotation instead.
 */
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Request attribute key under which the resolved {@link TokenClaims} is stored,
     * so controller methods can access it via {@code request.getAttribute(...)}.
     */
    public static final String ATTR_TOKEN_CLAIMS = "texora.iam.tokenClaims";

    private final TokenValidator tokenValidator;

    public RbacInterceptor(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        // Non-controller handlers (e.g., resource handlers) — skip
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Step 1: Extract and validate the token — fail closed on any error
        TokenClaims claims = validateToken(request);

        // Step 2: Set tenant context for this request thread
        TenantContext.set(claims.getTenantId());

        // Step 3: Store claims in request attributes for optional controller access
        request.setAttribute(ATTR_TOKEN_CLAIMS, claims);

        // Step 4: Resolve required roles (method annotation takes precedence over class)
        RequiresRole methodAnnotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
        RequiresRole effective = (methodAnnotation != null)
            ? methodAnnotation
            : handlerMethod.getBeanType().getAnnotation(RequiresRole.class);

        if (effective == null) {
            // No @RequiresRole declared — endpoint is authenticated but not role-restricted.
            // Still passes because the token is valid and TenantContext is set.
            LOGGER.debug("No @RequiresRole on {} {} — authenticated access permitted",
                request.getMethod(), request.getRequestURI());
            return true;
        }

        // Step 5: Check roles — fail closed
        enforceRoles(claims, effective.value(), request);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        // Always clear TenantContext to prevent thread-pool leakage
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private TokenClaims validateToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new PlatformException.UnauthenticatedException(
                "MISSING_TOKEN",
                "Authorization header is missing or does not start with 'Bearer '");
        }
        // TokenValidator handles the rest — any failure throws UnauthenticatedException
        return tokenValidator.validate(authHeader);
    }

    private void enforceRoles(TokenClaims claims, String[] required,
                               HttpServletRequest request) {
        Set<String> callerRoles = claims.getRoles();
        boolean permitted = Arrays.stream(required).anyMatch(callerRoles::contains);

        if (!permitted) {
            LOGGER.warn(
                "RBAC denied: subject={} tenant={} required={} actual={} uri={}",
                claims.getSubject(), claims.getTenantId(),
                Arrays.toString(required), callerRoles,
                request.getRequestURI());

            throw PlatformException.ForbiddenException.rbacDenied(
                "Caller lacks required role. Required one of: " + Arrays.toString(required));
        }

        LOGGER.debug("RBAC permitted: subject={} tenant={} uri={}",
            claims.getSubject(), claims.getTenantId(), request.getRequestURI());
    }
}
