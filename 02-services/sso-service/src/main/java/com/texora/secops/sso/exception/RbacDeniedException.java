package com.texora.secops.sso.exception;

/** Thrown by RbacInterceptor when the caller lacks the required role. Maps to 403. */
public class RbacDeniedException extends RuntimeException {

    public RbacDeniedException(String message) {
        super(message);
    }
}
