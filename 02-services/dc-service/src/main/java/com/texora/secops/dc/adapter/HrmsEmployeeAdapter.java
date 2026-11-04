package com.texora.secops.dc.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * THE single seam between DC and the HRMS (ADR D-2: HRMS is the employee system of record).
 * No other DC class may talk to HRMS directly.
 *
 * <p>HRMS currently exposes NO API or event stream, so the only implementation is
 * {@link LocalStubHrmsEmployeeAdapter}. When HRMS ships an API/event feed, add a real implementation of this
 * interface (select it with dc.hrms.adapter=rest) - nothing else in DC changes.
 *
 * <p>Implementations must throw an unchecked exception on any transport/auth failure; callers fail closed.
 */
public interface HrmsEmployeeAdapter {

    Optional<HrmsEmployee> fetchEmployee(UUID tenantId, String hrmsEmployeeId);

    /** since == null means "everything". */
    List<HrmsEmployee> fetchChangedSince(UUID tenantId, Instant since);
}
