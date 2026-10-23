package com.texora.secops.vpn.policy;

/** The conditions document is malformed, unknown-field-bearing or semantically invalid. */
public class PolicyConditionsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PolicyConditionsException(String message) {
        super(message);
    }

    public PolicyConditionsException(String message, Throwable cause) {
        super(message, cause);
    }
}
