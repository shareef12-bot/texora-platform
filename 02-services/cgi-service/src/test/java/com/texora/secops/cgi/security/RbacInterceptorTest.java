package com.texora.secops.cgi.security;

import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacInterceptorTest {

    static class Dummy {
        @RequiresRole("cgi.route.read")
        public void secured() {
        }

        @AuthenticatedOnly
        public void authOnly() {
        }

        public void unannotated() {
        }
    }

    private final TokenValidator validator = mock(TokenValidator.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private final RbacInterceptor interceptor = new RbacInterceptor(validator, audit);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final UUID tenant = UUID.randomUUID();
    private final UUID subject = UUID.randomUUID();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/v1/route-allowlist");
        request.addHeader("Authorization", "Bearer good");
    }

    private TokenClaims claims(String... roles) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        when(c.getRoles()).thenReturn(Set.of(roles));
        return c;
    }

    private HandlerMethod handler(String name) throws Exception {
        return new HandlerMethod(new Dummy(), Dummy.class.getMethod(name));
    }

    private void assertRejected(Object handler, int status, String code) {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOfSatisfying(CgiApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(status);
                    assertThat(e.getCode()).isEqualTo(code);
                });
    }

    @Test
    void missingOrBlankHeaderIs401WithoutCallingTheValidator() throws Exception {
        request = new MockHttpServletRequest("GET", "/x");
        assertRejected(handler("secured"), 401, "UNAUTHENTICATED");
        request.addHeader("Authorization", "  ");
        assertRejected(handler("secured"), 401, "UNAUTHENTICATED");
        verify(validator, never()).validate(any());
    }

    @Test
    void invalidTokenIs401() throws Exception {
        PlatformException.UnauthenticatedException denied = mock(PlatformException.UnauthenticatedException.class);
        when(validator.validate("Bearer good")).thenThrow(denied);
        assertRejected(handler("secured"), 401, "UNAUTHENTICATED");
    }

    @Test
    void validatorFaultFailsClosedAs401() throws Exception {
        when(validator.validate("Bearer good")).thenThrow(new IllegalStateException("jwks down"));
        assertRejected(handler("secured"), 401, "UNAUTHENTICATED");
    }

    @Test
    void nullClaimsAre401() throws Exception {
        when(validator.validate("Bearer good")).thenReturn(null);
        assertRejected(handler("secured"), 401, "UNAUTHENTICATED");
    }

    @Test
    void missingRoleIs403AndAudited() throws Exception {
        TokenClaims granted = claims("cgi.other");
        when(validator.validate("Bearer good")).thenReturn(granted);
        assertRejected(handler("secured"), 403, "RBAC_DENIED");
        verify(audit).recordNeverFail(any());
    }

    @Test
    void requiredRoleIsAllowedAndCallerIsPublished() throws Exception {
        TokenClaims granted = claims("cgi.route.read");
        when(validator.validate("Bearer good")).thenReturn(granted);
        assertThat(interceptor.preHandle(request, response, handler("secured"))).isTrue();
        CallerContext caller = (CallerContext) request.getAttribute(CallerContext.REQUEST_ATTRIBUTE);
        assertThat(caller.getTenantId()).isEqualTo(tenant);
    }

    @Test
    void authenticatedOnlyNeedsNoRole() throws Exception {
        TokenClaims granted = claims();
        when(validator.validate("Bearer good")).thenReturn(granted);
        assertThat(interceptor.preHandle(request, response, handler("authOnly"))).isTrue();
    }

    @Test
    void handlerWithoutAccessRuleIsDeniedFailClosed() throws Exception {
        TokenClaims granted = claims("cgi.route.read");
        when(validator.validate("Bearer good")).thenReturn(granted);
        assertRejected(handler("unannotated"), 403, "RBAC_NOT_CONFIGURED");
    }

    @Test
    void nonControllerHandlerIsDenied() {
        TokenClaims granted = claims("cgi.route.read");
        when(validator.validate("Bearer good")).thenReturn(granted);
        assertRejected(new Object(), 403, "RBAC_NOT_CONFIGURED");
    }
}
