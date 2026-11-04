package com.texora.secops.dc;

import java.util.UUID;

/** Service-wide constants. */
public final class DcConstants {

    /** Actor id recorded for system-initiated work (scheduled HRMS pull, sweepers). */
    public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    public static final String SYNC_SOURCE_LDAP_PUSH = "LDAP_PUSH";
    public static final String SYNC_SOURCE_HRMS_PULL = "HRMS_PULL";

    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_GROUP = "GROUP";
    public static final String SUBJECT_ROLE = "ROLE";

    private DcConstants() {
    }
}
