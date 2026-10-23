package com.texora.secops.ldap.sync.client;

/**
 * Thrown by {@link LdapEngineAdapter} implementations when the LDAP engine is
 * unreachable, returns an error, or behaves unexpectedly.
 *
 * <p>Callers must treat this as a DENY or leave pending changes in PENDING state.
 * Never swallow this exception — fail closed.
 */
public class LdapEngineException extends RuntimeException {

    public LdapEngineException(String message) {
        super(message);
    }

    public LdapEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
