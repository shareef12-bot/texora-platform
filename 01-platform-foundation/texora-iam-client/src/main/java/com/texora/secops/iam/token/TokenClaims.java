package com.texora.secops.iam.token;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable value object representing the claims extracted from a validated
 * SSO-issued OAuth2/OIDC bearer token.
 *
 * <p>Populated by {@link TokenValidator} after signature and expiry checks pass.
 * Stored in the Spring Security context so that downstream components (services,
 * repositories) can access tenant and subject without touching the raw JWT.
 *
 * <p>No Lombok — explicit constructor and getters only (standard B.2).
 */
public final class TokenClaims {

    /** The tenant this token was issued for. Set into {@link com.texora.secops.starter.domain.TenantContext}. */
    private final UUID tenantId;

    /** The authenticated subject (user or service account ID). */
    private final UUID subject;

    /** The caller's email / human-readable identifier (may be null for service accounts). */
    private final String email;

    /** Roles granted to this principal for the current tenant. */
    private final Set<String> roles;

    /** Raw JWT ID — useful for audit trails. */
    private final String jwtId;

    public TokenClaims(UUID tenantId, UUID subject, String email,
                       Set<String> roles, String jwtId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.subject  = Objects.requireNonNull(subject,  "subject");
        this.email    = email;
        this.roles    = Collections.unmodifiableSet(
            Objects.requireNonNull(roles, "roles"));
        this.jwtId    = jwtId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public String getJwtId() {
        return jwtId;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TokenClaims)) return false;
        TokenClaims that = (TokenClaims) other;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(subject, that.subject)
            && Objects.equals(jwtId, that.jwtId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, subject, jwtId);
    }

    @Override
    public String toString() {
        return "TokenClaims{tenantId=" + tenantId
            + ", subject=" + subject
            + ", roles=" + roles + "}";
    }
}
