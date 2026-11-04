package com.texora.secops.ldap.exception;

/**
 * Thrown when a schema-change submitter attempts to approve their own change.
 * The two-person rule requires that the approver differ from the submitter.
 */
public class SelfApprovalException extends RuntimeException {

    public SelfApprovalException(String message) {
        super(message);
    }
}
