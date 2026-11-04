package com.texora.secops.siem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.siem.dto.common.ErrorResponse;
import com.texora.secops.starter.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Validates the resolved SSO identity and enforces {@link RequiresRole} on the target handler
 * method, exactly per B.6.2/B.6.3: authorization runs before the handler, and any ambiguity -
 * missing header, expired/malformed token, missing claim - denies rather than allows. Also
 * populates {@link CallerContext} for the duration of the request so downstream services never
 * need to re-derive the caller's tenant from anything other than the validated token.
 *
 * <p>Per the CONFIRMED standard pattern in KNOWN-INTEGRATION-PITFALLS.md item 1, token
 * validation is delegated entirely to {@code texora-iam-client}'s {@link TokenValidator}, called
 * directly on the raw {@code Authorization} header - NOT via Spring Security's
 * {@code SecurityContextHolder}/{@code JwtAuthenticationToken} machinery, which this service's
 * earlier draft used before that library's real shape was confirmed. {@code TokenValidator}
 * fails closed on any failure (missing/expired/malformed/missing-claim/invalid-UUID) by throwing
 * {@code PlatformException.UnauthenticatedException} and never returns a partial
 * {@link TokenClaims} - this class does not need its own fallback validation logic on top of
 * that guarantee.
 *
 * <p><b>Open item:</b> the package for {@code PlatformException} itself (as opposed to
 * {@code TokenValidator}/{@code TokenClaims}, whose package IS confirmed as
 * {@code com.texora.secops.iam.token}) was not given verbatim in the pitfalls doc - it is
 * imported here from {@code com.texora.secops.starter.exception} on the inference that it lives
 * alongside the other confirmed shared exception, {@code TenantContextMissingException}. Confirm
 * this import against the real library before relying on it; if the compiler reports it wrong,
 * that is expected and should be fixed with the true package rather than guessed further.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final TokenValidator tokenValidator;

    public RbacInterceptor(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        TokenClaims claims;
        try {
            // Standard pattern per KNOWN-INTEGRATION-PITFALLS.md item 1.
            claims = tokenValidator.validate(request.getHeader("Authorization"));
        } catch (PlatformException.UnauthenticatedException e) {
            writeError(response, 401, "Unauthorized", "AUTH_REQUIRED", "Valid SSO-issued bearer token required", request);
            return false;
        } catch (Exception ex) {
            // Any other ambiguity (unexpected exception shape, etc.) also fails closed - never
            // let an exception path be interpreted as success (B.6.3).
            LOGGER.warn("Unexpected failure resolving caller identity - failing closed: {}", ex.getMessage());
            writeError(response, 401, "Unauthorized", "AUTH_REQUIRED", "Valid SSO-issued bearer token required", request);
            return false;
        }

        CallerContext.set(claims);

        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            // Fail closed: every admin/analyst endpoint must be explicitly annotated. An
            // endpoint with no annotation is treated as misconfigured, not as "open".
            LOGGER.error("Handler {} has no @RequiresRole - denying by default (fail closed)",
                    handlerMethod.getMethod());
            writeError(response, 403, "Forbidden", "RBAC_DENIED", "Endpoint has no declared RBAC policy", request);
            return false;
        }

        if (!hasAnyRole(claims, requiresRole.value())) {
            writeError(response, 403, "Forbidden", "RBAC_DENIED", "Caller lacks required role", request);
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        CallerContext.clear();
    }

    private boolean hasAnyRole(TokenClaims claims, String[] required) {
        if (required == null || required.length == 0) {
            return true;
        }
        for (String role : required) {
            if (claims.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String error, String code,
                             String message, HttpServletRequest request) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(Instant.now(), status, error, code, message,
                    (String) request.getAttribute("traceId"));
            response.getWriter().write(MAPPER.writeValueAsString(body));
        } catch (Exception writeEx) {
            LOGGER.error("Failed to write RBAC error response", writeEx);
        }
    }
}