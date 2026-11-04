package com.texora.secops.siem.domain.enums;

/** Lifecycle: OPEN -> TRIAGED -> ESCALATED -> CLOSED. See AlertTransitionRules for the
 *  allowed-transition graph enforced by AlertLifecycleService. */
public enum AlertStatus {
    OPEN,
    TRIAGED,
    ESCALATED,
    CLOSED
}
