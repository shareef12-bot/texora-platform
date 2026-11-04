package com.texora.secops.siem.alerting.model;

import com.texora.secops.siem.domain.enums.AlertStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The alert lifecycle graph: OPEN -&gt; TRIAGED -&gt; ESCALATED -&gt; CLOSED, with a direct
 * OPEN/TRIAGED -&gt; CLOSED shortcut for the common false-positive case. CLOSED is terminal -
 * a closed alert can never be reopened through this API (a fresh rule match creates a new
 * alert instead). Enforced by {@code AlertLifecycleService}, never left to callers to honor.
 */
public final class AlertTransitionRules {

    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED = new EnumMap<>(AlertStatus.class);

    static {
        ALLOWED.put(AlertStatus.OPEN, EnumSet.of(AlertStatus.TRIAGED, AlertStatus.CLOSED));
        ALLOWED.put(AlertStatus.TRIAGED, EnumSet.of(AlertStatus.ESCALATED, AlertStatus.CLOSED));
        ALLOWED.put(AlertStatus.ESCALATED, EnumSet.of(AlertStatus.CLOSED));
        ALLOWED.put(AlertStatus.CLOSED, EnumSet.noneOf(AlertStatus.class));
    }

    private AlertTransitionRules() {
    }

    public static boolean isAllowed(AlertStatus from, AlertStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(AlertStatus.class)).contains(to);
    }
}
