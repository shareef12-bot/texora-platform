package com.texora.secops.ldap.security;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps HTTP method + path patterns to required LDAP RBAC roles.
 *
 * <p>Evaluated in order; first match wins. Any unmapped endpoint is denied
 * (fail-closed).
 */
@Component
public class EndpointRoleRegistry {

    /** Role constants as defined in the LDAP LLD §6.1. */
    public static final String ROLE_SEARCH_READ   = "ldap.search.read";
    public static final String ROLE_SYNC_TRIGGER  = "ldap.sync.trigger";
    public static final String ROLE_SCHEMA_READ   = "ldap.schema.read";
    public static final String ROLE_SCHEMA_WRITE  = "ldap.schema.write";
    public static final String ROLE_SCHEMA_APPROVE = "ldap.schema.approve";
    public static final String ROLE_BIND_READ     = "ldap.bind.read";
    public static final String ROLE_BIND_WRITE    = "ldap.bind.write";
    public static final String ROLE_CONFIG_READ   = "ldap.config.read";
    public static final String ROLE_CONFIG_WRITE  = "ldap.config.write";
    public static final String ROLE_POLICY_READ   = "ldap.policy.read";
    public static final String ROLE_AUDIT_READ    = "ldap.audit.read";

    private record RoleRule(String method, Pattern pathPattern, String role) {}

    private final java.util.List<RoleRule> rules;

    public EndpointRoleRegistry() {
        rules = new java.util.ArrayList<>();
        // Directory search endpoints
        add("GET",  "/api/v1/directory/entries",            ROLE_SEARCH_READ);
        add("GET",  "/api/v1/directory-search",             ROLE_SEARCH_READ);
        // Sync trigger
        add("POST", "/api/v1/directory/sync",               ROLE_SYNC_TRIGGER);
        // Schema management
        add("GET",  "/api/v1/schema/attribute-management",  ROLE_SCHEMA_READ);
        add("POST", "/api/v1/schema/attribute-management",  ROLE_SCHEMA_WRITE);
        add("POST", "/api/v1/schema/[^/]+/decision",        ROLE_SCHEMA_APPROVE);
        // Bind configuration
        add("GET",  "/api/v1/secure-bind-configuration",    ROLE_BIND_READ);
        add("POST", "/api/v1/secure-bind-configuration",    ROLE_BIND_WRITE);
        // Config
        add("GET",  "/api/v1/config",                       ROLE_CONFIG_READ);
        add("POST", "/api/v1/config",                       ROLE_CONFIG_WRITE);
        // Policies
        add("GET",  "/api/v1/policies",                     ROLE_POLICY_READ);
        // Audit
        add("GET",  "/api/v1/audit",                        ROLE_AUDIT_READ);
    }

    private void add(String method, String pathPattern, String role) {
        // Convert simple patterns (with [^/]+ for path vars) to regex
        String regex = "^" + pathPattern + "(/.*)?$";
        rules.add(new RoleRule(method.toUpperCase(), Pattern.compile(regex), role));
    }

    /**
     * Returns the required role for the given HTTP method and path, or {@code null}
     * if no mapping exists (caller should deny).
     */
    public String requiredRole(String method, String path) {
        String normalizedPath = path.split("\\?")[0]; // strip query string
        for (RoleRule rule : rules) {
            if (rule.method().equalsIgnoreCase(method)
                    && rule.pathPattern().matcher(normalizedPath).matches()) {
                return rule.role();
            }
        }
        return null; // unmapped → deny
    }
}
