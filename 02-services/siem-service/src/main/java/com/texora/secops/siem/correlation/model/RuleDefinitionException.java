package com.texora.secops.siem.correlation.model;

/** A rule's stored definition is malformed or fails structural validation. Thrown both at
 *  submission time (RuleApprovalService rejects the request outright with 422) and, defensively,
 *  at evaluation time (RuleExecutionSandbox isolates it to this one rule - Requirement 2). */
public class RuleDefinitionException extends RuntimeException {

    public RuleDefinitionException(String message) {
        super(message);
    }

    public RuleDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
