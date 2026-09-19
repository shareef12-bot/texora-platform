package com.texora.secops.sso.exception;

/**
 * Thrown whenever policy/consent/MFA state is missing or ambiguous. Always
 * maps to a DENY outcome (422 or 403 depending on context) — this exception
 * type must never be caught and treated as success anywhere in the codebase.
 */
public class FailClosedException extends RuntimeException {

    private final String code;

    public FailClosedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
