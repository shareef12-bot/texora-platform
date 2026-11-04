package com.texora.secops.vpn.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.exception.RbacDeniedException;
import com.texora.secops.vpn.exception.VpnException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacInterceptorTest {

    static class Handlers {
        @RequiresRole({"vpn.policy.read", "vpn.policy.write"})
        public void protectedOp() {
        }

        public void unannotated() {
        }

        @MtlsOnly
        public void dataplane() {
        }

        @MtlsOnly
        @RequiresRole("vpn.policy.read")
        public void both() {
        }
    }

    private final TokenValidator validator = mock(TokenValidator.class);
    private final VpnProperties props = new VpnProperties();
    private final RbacInterceptor interceptor = new RbacInterceptor(validator, props);
    private final Handlers bean = new Handlers();
    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc");
    }

    private HandlerMethod handler(String name) throws Exception {
        return new HandlerMethod(bean, Handlers.class.getMethod(name));
    }

    private TokenClaims claims(String... roles) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(UUID.randomUUID());
        when(c.getSubject()).thenReturn(UUID.randomUUID());
        when(c.hasRole(any())).thenReturn(false);
        for (String r : roles) {
            when(c.hasRole(r)).thenReturn(true);
        }
        return c;
    }

    @Test
    void nonHandlerMethodsPassThrough() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void endpointWithNoAccessRuleIsDeniedFailClosed() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("unannotated")))
                .isInstanceOf(RbacDeniedException.class);
    }

    @Test
    void ambiguousRuleIsDenied() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("both")))
                .isInstanceOf(RbacDeniedException.class);
    }

    @Test
    void validTokenWithRequiredRoleIsAllowedAndClaimsAreExposed() throws Exception {
        TokenClaims c = claims("vpn.policy.write");
        when(validator.validate("Bearer abc")).thenReturn(c);
        assertThat(interceptor.preHandle(request, response, handler("protectedOp"))).isTrue();
        assertThat(request.getAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE)).isSameAs(c);
    }

    @Test
    void validTokenWithoutRoleIsForbidden() throws Exception {
        TokenClaims lowClaims = claims("vpn.audit.read");
        when(validator.validate("Bearer abc")).thenReturn(lowClaims);
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("protectedOp")))
                .isInstanceOf(RbacDeniedException.class);
        assertThat(request.getAttribute(RbacInterceptor.CLAIMS_ATTRIBUTE)).isNull();
    }

    @Test
    void invalidTokenPropagatesUnauthenticated() throws Exception {
        when(validator.validate(any())).thenThrow(PlatformException.UnauthenticatedException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("protectedOp")))
                .isInstanceOf(PlatformException.UnauthenticatedException.class);
    }

    @Test
    void claimsMissingTenantOrSubjectAreRejected() throws Exception {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(null);
        when(c.getSubject()).thenReturn(UUID.randomUUID());
        when(validator.validate(any())).thenReturn(c);
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("protectedOp")))
                .isInstanceOf(VpnException.class).extracting(e -> ((VpnException) e).getStatus()).isEqualTo(401);
    }

    // ---------------- mTLS ----------------

    private X509Certificate cert(String dn) {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal(dn));
        return cert;
    }

    @Test
    void dataplaneEndpointWithoutClientCertIs401() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("dataplane")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void dataplaneEndpointIgnoresBearerTokens() throws Exception {
        // A valid SSO token must NOT authenticate a dataplane call.
        TokenClaims dataplaneClaims = claims("vpn.policy.write");
        when(validator.validate(any())).thenReturn(dataplaneClaims);
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("dataplane")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getCode()).isEqualTo("MTLS_REQUIRED"));
    }

    @Test
    void certificateNotInAllowListIs403AndEmptyAllowListRejectsEverything() throws Exception {
        request.setAttribute(RbacInterceptor.X509_ATTRIBUTE, new X509Certificate[]{cert("CN=dataplane")});
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("dataplane")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        props.getDataplane().getAllowedClientSubjects().add("CN=someone-else");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("dataplane")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getCode()).isEqualTo("MTLS_SUBJECT_NOT_ALLOWED"));
    }

    @Test
    void allowListedCertificateIsAccepted() throws Exception {
        props.getDataplane().getAllowedClientSubjects().add("CN=dataplane");
        request.setAttribute(RbacInterceptor.X509_ATTRIBUTE, new X509Certificate[]{cert("CN=dataplane")});
        assertThat(interceptor.preHandle(request, response, handler("dataplane"))).isTrue();
    }

    @Test
    void expiredCertificateIsRejected() throws Exception {
        props.getDataplane().getAllowedClientSubjects().add("CN=dataplane");
        X509Certificate c = cert("CN=dataplane");
        doThrow(new CertificateExpiredException("expired")).when(c).checkValidity();
        request.setAttribute(RbacInterceptor.X509_ATTRIBUTE, new X509Certificate[]{c});
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("dataplane")))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getCode()).isEqualTo("MTLS_CERT_INVALID"));
    }
}
