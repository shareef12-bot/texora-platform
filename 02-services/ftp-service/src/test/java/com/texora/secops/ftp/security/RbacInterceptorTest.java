package com.texora.secops.ftp.security;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.exception.FtpUnauthenticatedException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RbacInterceptorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    static class Probe {
        @RequiresRole("ftp.job.read")
        public void guarded() {
        }

        public void unguarded() {
        }
    }

    private final TokenValidator validator = mock(TokenValidator.class);
    private final FtpAuditTrail audit = mock(FtpAuditTrail.class);
    private final RbacInterceptor interceptor = new RbacInterceptor(validator, audit);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        TenantBinder.clear();
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Probe(), Probe.class.getMethod(method));
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/transfer-jobs");
        if (authorization != null) {
            r.addHeader("Authorization", authorization);
        }
        return r;
    }

    private static TokenClaims claims(String... roles) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(TENANT);
        when(c.getSubject()).thenReturn(SUBJECT);
        when(c.getRoles()).thenReturn(Set.of(roles));
        when(c.hasRole(anyString())).thenAnswer(inv -> Set.of(roles).contains(inv.<String>getArgument(0)));
        return c;
    }

    @Test
    void missingAuthorizationHeaderIsUnauthenticated() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request(null), response, handler("guarded")))
                .isInstanceOf(FtpUnauthenticatedException.class);
        verifyNoInteractions(validator);
    }

    @Test
    void invalidTokenIsUnauthenticated() throws Exception {
        when(validator.validate(any())).thenThrow(PlatformException.UnauthenticatedException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer bad"), response, handler("guarded")))
                .isInstanceOf(FtpUnauthenticatedException.class);
    }

    @Test
    void unexpectedValidatorErrorFailsClosed() throws Exception {
        when(validator.validate(any())).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, handler("guarded")))
                .isInstanceOf(FtpUnauthenticatedException.class);
    }

    @Test
    void nullClaimsFailClosed() throws Exception {
        when(validator.validate(any())).thenReturn(null);
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, handler("guarded")))
                .isInstanceOf(FtpUnauthenticatedException.class);
    }

    @Test
    void callerWithoutRequiredRoleIsDeniedAndAudited() throws Exception {
        TokenClaims callerClaims = claims("ftp.audit.read");
        when(validator.validate(any())).thenReturn(callerClaims);
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, handler("guarded")))
                .isInstanceOf(RbacDeniedException.class);
        verify(audit).rbacDenied(eq(TENANT), eq(SUBJECT.toString()), anyMap());
    }

    @Test
    void auditFailureDoesNotTurnADenialIntoSuccess() throws Exception {
        TokenClaims callerClaims = claims("nothing");
        when(validator.validate(any())).thenReturn(callerClaims);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(audit).rbacDenied(any(), any(), anyMap());
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, handler("guarded")))
                .isInstanceOf(RbacDeniedException.class);
    }

    @Test
    void handlerWithoutRequiresRoleIsDeniedEvenForValidToken() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, handler("unguarded")))
                .isInstanceOf(RbacDeniedException.class)
                .extracting("code").isEqualTo("RBAC_NO_POLICY");
        verifyNoInteractions(validator);
    }

    @Test
    void nonControllerHandlerIsDenied() {
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer x"), response, new Object()))
                .isInstanceOf(RbacDeniedException.class);
    }

    @Test
    void permittedCallerGetsContextAndItIsClearedAfterCompletion() throws Exception {
        TokenClaims callerClaims = claims("ftp.job.read");
        when(validator.validate("Bearer ok")).thenReturn(callerClaims);
        MockHttpServletRequest req = request("Bearer ok");
        HandlerMethod h = handler("guarded");

        assertThat(interceptor.preHandle(req, response, h)).isTrue();
        CallerAuthentication caller = CallerAuthentication.require();
        assertThat(caller.getTenantId()).isEqualTo(TENANT);
        assertThat(caller.getSubject()).isEqualTo(SUBJECT);
        assertThat(caller.getActorId()).isEqualTo(SUBJECT.toString());

        interceptor.afterCompletion(req, response, h, null);
        assertThatThrownBy(CallerAuthentication::require).isInstanceOf(FtpUnauthenticatedException.class);
    }
}
