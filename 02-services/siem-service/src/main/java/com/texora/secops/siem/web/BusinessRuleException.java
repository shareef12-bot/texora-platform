package com.texora.secops.siem.web;

/** e.g. an illegal alert-status transition, or a rule-approval self-approval attempt. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
