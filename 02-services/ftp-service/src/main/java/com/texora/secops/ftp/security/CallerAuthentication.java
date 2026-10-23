package com.texora.secops.ftp.security;

import com.texora.secops.ftp.exception.FtpUnauthenticatedException;
import com.texora.secops.iam.token.TokenClaims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The authenticated caller for the current thread, derived ONLY from validated SSO token claims.
 * Placed in the SecurityContext so the shared Audit SDK aspect can resolve the actor.
 * Also used (as a SYSTEM principal) on the async orchestration threads.
 */
public class CallerAuthentication extends AbstractAuthenticationToken {

    public static final UUID SYSTEM_SUBJECT = new UUID(0L, 0L);
    public static final String SYSTEM_ACTOR = "SYSTEM:ftp-orchestrator";

    private final UUID tenantId;
    private final UUID subject;
    private final Set<String> roles;
    private final boolean system;

    private CallerAuthentication(UUID tenantId, UUID subject, Set<String> roles, boolean system) {
        super(toAuthorities(roles));
        this.tenantId = tenantId;
        this.subject = subject;
        this.roles = roles;
        this.system = system;
        super.setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> toAuthorities(Set<String> roles) {
        List<GrantedAuthority> list = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return list;
    }

    public static CallerAuthentication of(TokenClaims claims) {
        return new CallerAuthentication(claims.getTenantId(), claims.getSubject(), Set.copyOf(claims.getRoles()), false);
    }

    public static CallerAuthentication system(UUID tenantId) {
        return new CallerAuthentication(tenantId, SYSTEM_SUBJECT, Set.of(), true);
    }

    /** The caller for this request; throws 401 if there is none (fail closed). */
    public static CallerAuthentication require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CallerAuthentication) {
            return (CallerAuthentication) auth;
        }
        throw new FtpUnauthenticatedException("No authenticated caller");
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

    public boolean isSystem() {
        return system;
    }

    /** Actor identifier written to ftp_audit_event.actor_id. */
    public String getActorId() {
        return system ? SYSTEM_ACTOR : subject.toString();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }
}
