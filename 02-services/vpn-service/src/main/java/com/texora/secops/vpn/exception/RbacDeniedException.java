package com.texora.secops.vpn.exception;

/** Caller lacks the required role, or the endpoint declares no access rule (fail closed). */
public class RbacDeniedException extends VpnException {

    private static final long serialVersionUID = 1L;

    public RbacDeniedException(String message) {
        super(403, "RBAC_DENIED", message);
    }
}
