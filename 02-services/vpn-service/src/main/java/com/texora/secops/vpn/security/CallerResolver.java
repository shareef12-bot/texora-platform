package com.texora.secops.vpn.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.vpn.exception.VpnException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Resolves the authenticated caller for the current request. The tenant comes ONLY from the validated SSO token
 * claim (01-SHARED-STANDARDS B.5), never from a request parameter.
 */
@Component
public class CallerResolver {

    public Caller current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Object claims = attrs == null ? null
                : attrs.getAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(claims instanceof TokenClaims)) {
            throw VpnException.unauthenticated("UNAUTHENTICATED", "Authentication required");
        }
        TokenClaims tokenClaims = (TokenClaims) claims;
        return new Caller(tokenClaims.getTenantId(), tokenClaims.getSubject());
    }
}
