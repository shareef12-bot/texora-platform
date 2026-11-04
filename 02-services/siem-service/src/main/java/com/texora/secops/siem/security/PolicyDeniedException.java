package com.texora.secops.siem.security;

/** A live SEC policy check denied the requested action (distinct from a static RBAC role check,
 *  which CrossTenantAccessException/RbacInterceptor already cover). Mapped to 403. */
public class PolicyDeniedException extends RuntimeException {

    public PolicyDeniedException(String message) {
        super(message);
    }
}
