package com.texora.secops.cgi.security;

import com.texora.secops.iam.token.TokenClaims;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable view of the validated SSO token. The tenant is ALWAYS taken from here (token claim) and never
 * from a request parameter (decision D-1, Shared Standards B.5).
 */
public final class CallerContext {

    /** Request attribute under which {@link RbacInterceptor} publishes the caller. */
    public static final String REQUEST_ATTRIBUTE = "com.texora.secops.cgi.callerContext";

    private final UUID tenantId;
    private final UUID subject;
    private final Set<String> roles;

    public CallerContext(UUID tenantId, UUID subject, Set<String> roles) {
        this.tenantId = tenantId;
        this.subject = subject;
        this.roles = roles == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(roles));
    }

    public static CallerContext from(TokenClaims claims) {
        return new CallerContext(claims.getTenantId(), claims.getSubject(), claims.getRoles());
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSubject() {
        return subject;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "CallerContext{tenantId=" + tenantId + ", subject=" + subject + "}";
    }
}
