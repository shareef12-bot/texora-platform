package com.texora.secops.vpn.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.exception.RbacDeniedException;
import com.texora.secops.vpn.exception.VpnException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Enforces authentication and per-endpoint roles BEFORE any handler runs. Fail closed:
 * <ul>
 *   <li>@MtlsOnly -> client certificate required and its subject must be in the allow-list</li>
 *   <li>@RequiresRole -> valid SSO token (shared TokenValidator) holding one of the roles</li>
 *   <li>neither annotation -> DENY (an endpoint someone forgot to protect is never open)</li>
 * </ul>
 * No credential check of our own: token validation is delegated to the shared IAM client library.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    public static final String CLAIMS_ATTRIBUTE = RbacInterceptor.class.getName() + ".CLAIMS";
    static final String X509_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private static final Logger LOGGER = LoggerFactory.getLogger(RbacInterceptor.class);

    private final TokenValidator tokenValidator;
    private final VpnProperties properties;

    public RbacInterceptor(TokenValidator tokenValidator, VpnProperties properties) {
        this.tokenValidator = tokenValidator;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        MtlsOnly mtls = method.getMethodAnnotation(MtlsOnly.class);
        RequiresRole required = method.getMethodAnnotation(RequiresRole.class);

        if (mtls != null && required != null) {
            LOGGER.error("Endpoint {} declares both @MtlsOnly and @RequiresRole; denying", method);
            throw new RbacDeniedException("Endpoint access rule is ambiguous");
        }
        if (mtls != null) {
            requireDataplaneCertificate(request);
            return true;
        }
        if (required == null) {
            LOGGER.error("Endpoint {} has no access rule; denying (fail closed)", method);
            throw new RbacDeniedException("No access rule is defined for this endpoint");
        }

        TokenClaims claims;
        try {
            claims = tokenValidator.validate(request.getHeader("Authorization"));
        } catch (PlatformException.UnauthenticatedException e) {
            throw e; // mapped to 401 by the exception handler
        }
        if (claims == null || claims.getTenantId() == null || claims.getSubject() == null) {
            throw VpnException.unauthenticated("UNAUTHENTICATED", "Authentication required");
        }
        boolean allowed = false;
        for (String role : required.value()) {
            if (claims.hasRole(role)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            LOGGER.info("RBAC denied: subject={} tenant={} needs one of {}", claims.getSubject(), claims.getTenantId(),
                    List.of(required.value()));
            throw new RbacDeniedException("Caller lacks required role");
        }
        request.setAttribute(CLAIMS_ATTRIBUTE, claims);
        return true;
    }

    private void requireDataplaneCertificate(HttpServletRequest request) {
        Object attr = request.getAttribute(X509_ATTRIBUTE);
        if (!(attr instanceof X509Certificate[]) || ((X509Certificate[]) attr).length == 0) {
            throw VpnException.unauthenticated("MTLS_REQUIRED", "Client certificate required");
        }
        X509Certificate leaf = ((X509Certificate[]) attr)[0];
        try {
            leaf.checkValidity();
        } catch (CertificateException e) {
            throw VpnException.unauthenticated("MTLS_CERT_INVALID", "Client certificate is not valid");
        }
        String subject = leaf.getSubjectX500Principal().getName(X500Principal.RFC2253);
        List<String> allowed = properties.getDataplane().getAllowedClientSubjects();
        if (allowed == null || allowed.isEmpty() || !allowed.contains(subject)) {
            LOGGER.warn("Dataplane callback rejected: certificate subject not in allow-list");
            throw VpnException.forbidden("MTLS_SUBJECT_NOT_ALLOWED", "Client certificate is not an approved dataplane identity");
        }
    }
}
