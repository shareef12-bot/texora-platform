package com.texora.secops.ftp.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Status groupings shared by the services. */
public final class JobStatuses {

    /** States in which the orchestrator is allowed to keep working on a job. */
    public static final Set<JobStatus> RUNNING = Collections.unmodifiableSet(
            EnumSet.of(JobStatus.SUBMITTED, JobStatus.IN_PROGRESS, JobStatus.RESUMED));

    private JobStatuses() {
    }
}
