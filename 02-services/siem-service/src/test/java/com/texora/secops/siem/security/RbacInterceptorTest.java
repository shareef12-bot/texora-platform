package com.texora.secops.siem.security;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.exception.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B.6.2/B.6.3 - authorization runs before the handler, and every ambiguous case denies.
 *  Uses mocks for TokenValidator/TokenClaims/UnauthenticatedException because the pitfalls doc
 *  confirms their public read API but not their constructors - mocking avoids guessing them. */
class RbacInterceptorTest {

    private TokenValidator tokenValidator;
    private RbacInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenValidator = mock(TokenValidator.class);
        interceptor = new RbacInterceptor(tokenValidator);
    }

    @AfterEach
    void tearDown() {
        CallerContext.clear();
    }

    static class Handler {
        @RequiresRole("siem.alert.read")
        public void annotated() {
        }

        public void notAnnotated() {
        }
    }

    private TokenClaims claimsWithRoles(String... roles) {
        TokenClaims claims = mock(TokenClaims.class);
        when(claims.getTenantId()).thenReturn(UUID.randomUUID());
        when(claims.getSubject()).thenReturn(UUID.randomUUID());
        for (String role : roles) {
            when(claims.hasRole(role)).thenReturn(true);
        }
        return claims;
    }

    @Test
    void unauthenticatedTokenIsDeniedWithFourOhOne() throws Exception {
        when(tokenValidator.validate(any()))
                .thenThrow(mock(PlatformException.UnauthenticatedException.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handler(), "annotated"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void anyUnexpectedValidatorFailureAlsoFailsClosed() throws Exception {
        when(tokenValidator.validate(any())).thenThrow(new RuntimeException("boom"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handler(), "annotated"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void validTokenButMissingRoleIsDeniedWithFourOhThree() throws Exception {
        TokenClaims claims = claimsWithRoles("siem.audit.read");
        when(tokenValidator.validate(any())).thenReturn(claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handler(), "annotated"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void handlerWithNoRequiresRoleAnnotationIsDeniedByDefault() throws Exception {
        TokenClaims claims = claimsWithRoles("siem.alert.read");
        when(tokenValidator.validate(any())).thenReturn(claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handler(), "notAnnotated"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void validTokenWithCorrectRoleProceedsAndPopulatesCallerContext() throws Exception {
        TokenClaims claims = claimsWithRoles("siem.alert.read");
        when(tokenValidator.validate(any())).thenReturn(claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handler(), "annotated"));

        assertThat(proceed).isTrue();
        assertThat(CallerContext.get()).isNotNull();
    }
}