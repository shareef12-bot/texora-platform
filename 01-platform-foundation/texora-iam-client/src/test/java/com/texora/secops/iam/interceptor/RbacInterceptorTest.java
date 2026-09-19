package com.texora.secops.iam.interceptor;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import com.texora.secops.starter.domain.TenantContext;
import com.texora.secops.starter.exception.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacInterceptorTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private HandlerMethod handlerMethod;

    private RbacInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUBJECT   = UUID.randomUUID();
    private static final String VALID_TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        interceptor = new RbacInterceptor(tokenValidator);
        request     = new MockHttpServletRequest();
        response    = new MockHttpServletResponse();
        request.addHeader("Authorization", VALID_TOKEN);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void preHandle_sets_TenantContext_on_valid_token() throws Exception {
        TokenClaims claims = new TokenClaims(TENANT_ID, SUBJECT, null, Set.of("USER_READ"), null);
        when(tokenValidator.validate(VALID_TOKEN)).thenReturn(claims);
        when(handlerMethod.getMethodAnnotation(RequiresRole.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenAnswer(inv -> Object.class);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        assertEquals(TENANT_ID, TenantContext.get());
    }

    @Test
    void preHandle_permits_when_caller_holds_required_role() throws Exception {
        TokenClaims claims = new TokenClaims(TENANT_ID, SUBJECT, null, Set.of("ADMIN"), null);
        when(tokenValidator.validate(VALID_TOKEN)).thenReturn(claims);
        RequiresRole annotation = mockRequiresRole("ADMIN");
        when(handlerMethod.getMethodAnnotation(RequiresRole.class)).thenReturn(annotation);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    @Test
    void preHandle_denies_when_caller_lacks_required_role() throws Exception {
        TokenClaims claims = new TokenClaims(TENANT_ID, SUBJECT, null, Set.of("USER_READ"), null);
        when(tokenValidator.validate(VALID_TOKEN)).thenReturn(claims);
        RequiresRole annotation = mockRequiresRole("ADMIN");
        when(handlerMethod.getMethodAnnotation(RequiresRole.class)).thenReturn(annotation);

        assertThrows(PlatformException.ForbiddenException.class,
            () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void preHandle_denies_when_Authorization_header_missing() {
        request = new MockHttpServletRequest(); // no Authorization header

        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void preHandle_denies_when_token_invalid() {
        when(tokenValidator.validate(anyString()))
            .thenThrow(new PlatformException.UnauthenticatedException("INVALID_TOKEN", "bad token"));

        assertThrows(PlatformException.UnauthenticatedException.class,
            () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void afterCompletion_clears_TenantContext() throws Exception {
        TenantContext.set(TENANT_ID);
        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertFalse(TenantContext.isSet());
    }

    @Test
    void preHandle_permits_any_of_multiple_roles() throws Exception {
        TokenClaims claims = new TokenClaims(TENANT_ID, SUBJECT, null, Set.of("USER_READ"), null);
        when(tokenValidator.validate(VALID_TOKEN)).thenReturn(claims);
        RequiresRole annotation = mockRequiresRole("ADMIN", "USER_READ");
        when(handlerMethod.getMethodAnnotation(RequiresRole.class)).thenReturn(annotation);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    // ---- helpers ----

    private RequiresRole mockRequiresRole(String... roles) {
        return new RequiresRole() {
            @Override
            public Class<RequiresRole> annotationType() { return RequiresRole.class; }

            @Override
            public String[] value() { return roles; }
        };
    }
}
