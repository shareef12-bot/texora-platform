package com.texora.secops.dc.security;

import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacInterceptorTest {

    static class Dummy {
        @RequiresRole("dc.user.read")
        public void guarded() {
        }

        public void undeclared() {
        }
    }

    private TokenValidator validator;
    private TenantResolver tenantResolver;
    private DcAuditRecorder audit;
    private RbacInterceptor interceptor;
    private final UUID tenant = UUID.randomUUID();
    private final UUID subject = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = mock(TokenValidator.class);
        tenantResolver = mock(TenantResolver.class);
        audit = mock(DcAuditRecorder.class);
        interceptor = new RbacInterceptor(validator, tenantResolver, audit);
    }

    private TokenClaims claims(String... roles) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        when(c.getRoles()).thenReturn(new HashSet<String>(Arrays.asList(roles)));
        for (String r : roles) {
            when(c.hasRole(r)).thenReturn(true);
        }
        return c;
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/users");
        if (authorization != null) {
            r.addHeader("Authorization", authorization);
        }
        return r;
    }

    private HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Dummy(), Dummy.class.getMethod(method));
    }

    @Test
    void missingTokenIs401() throws Exception {
        DcException e = assertThrows(DcException.class, () -> interceptor.preHandle(request(null), new MockHttpServletResponse(), handler("guarded")));
        assertEquals(401, e.getStatus().value());
    }

    @Test
    void validatorFailureIs401() throws Exception {
        when(validator.validate("Bearer bad")).thenThrow(new IllegalStateException("expired"));
        assertEquals(401, assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer bad"), new MockHttpServletResponse(), handler("guarded"))).getStatus().value());
    }

    @Test
    void nullClaimsAre401() throws Exception {
        when(validator.validate("Bearer x")).thenReturn(null);
        assertEquals(401, assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer x"), new MockHttpServletResponse(), handler("guarded"))).getStatus().value());
    }

    @Test
    void allowedRoleProceedsAndExposesCaller() throws Exception {
        TokenClaims okClaims = claims("dc.user.read");
        when(validator.validate("Bearer ok")).thenReturn(okClaims);
        MockHttpServletRequest req = request("Bearer ok");
        assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), handler("guarded")));
        CallerContext caller = (CallerContext) req.getAttribute(RbacInterceptor.CALLER_ATTRIBUTE);
        assertNotNull(caller);
        assertEquals(tenant, caller.getTenantId());
        assertEquals(subject, caller.getSubjectId());
    }

    @Test
    void missingRoleIs403AndAudited() throws Exception {
        TokenClaims lowClaims = claims("dc.group.read");
        when(validator.validate("Bearer low")).thenReturn(lowClaims);
        DcException e = assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer low"), new MockHttpServletResponse(), handler("guarded")));
        assertEquals(403, e.getStatus().value());
        verify(audit).recordIndependent(eq(tenant), eq(subject), eq("RBAC_DENIED"), anyString(), anyString(), eq("DENIED"), anyMap());
    }

    @Test
    void handlerWithoutDeclaredRoleIsDenied() throws Exception {
        TokenClaims okClaims = claims("dc.user.read");
        when(validator.validate("Bearer ok")).thenReturn(okClaims);
        assertEquals(403, assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer ok"), new MockHttpServletResponse(), handler("undeclared"))).getStatus().value());
    }

    @Test
    void tenantMismatchIsDenied() throws Exception {
        TokenClaims okClaims = claims("dc.user.read");
        when(validator.validate("Bearer ok")).thenReturn(okClaims);
        doThrow(DcException.forbidden("TENANT_MISMATCH", "x")).when(tenantResolver).assertConsistentWithSharedContext(any());
        assertEquals("TENANT_MISMATCH", assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer ok"), new MockHttpServletResponse(), handler("guarded"))).getCode());
    }

    @Test
    void auditFailureStillDenies() throws Exception {
        TokenClaims noRoleClaims = claims(new String[0]);
        when(validator.validate("Bearer low")).thenReturn(noRoleClaims);
        doThrow(new IllegalStateException("db")).when(audit).recordIndependent(any(), any(), anyString(), anyString(), anyString(), anyString(), anyMap());
        assertEquals(403, assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer low"), new MockHttpServletResponse(), handler("guarded"))).getStatus().value());
    }

    @Test
    void emptyRolesCollectionIsHandled() throws Exception {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        when(c.getRoles()).thenReturn(Collections.<String>emptySet());
        when(validator.validate("Bearer none")).thenReturn(c);
        assertEquals(403, assertThrows(DcException.class,
                () -> interceptor.preHandle(request("Bearer none"), new MockHttpServletResponse(), handler("guarded"))).getStatus().value());
    }
}
