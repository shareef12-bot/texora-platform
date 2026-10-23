package com.texora.secops.siem.audit;

import java.util.UUID;

/** Reserved actor id for audit events the platform generates itself (e.g. an alert created by
 *  the correlation engine, not by a human caller) - distinguishable from any real user id. */
public final class SystemActor {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemActor() {
    }
}
