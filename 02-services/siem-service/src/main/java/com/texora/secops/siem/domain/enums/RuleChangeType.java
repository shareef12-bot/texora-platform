package com.texora.secops.siem.domain.enums;

/** Every entry written to siem_rule_change_audit is one of these. A DISABLED change is as
 *  security-relevant as a new detection and is always audited (Requirement 4). */
public enum RuleChangeType {
    SUBMITTED,
    APPROVED,
    REJECTED,
    DISABLED
}
