package com.texora.secops.dc.sync.service;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.admin.service.UserLifecycleService;
import com.texora.secops.dc.adapter.HrmsEmployee;
import com.texora.secops.dc.adapter.HrmsEmployeeAdapter;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcSyncJob;
import com.texora.secops.dc.exception.DcException;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.SyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HRMS -> DC projection pull (ADR D-2). Adds a second dc_sync_job source (HRMS_PULL) next to LDAP_PUSH.
 * Pull-based because HRMS has no event stream yet; an event-driven trigger can later call {@link #pull} too.
 * Each employee is applied in its own transaction so one bad record cannot abort the batch.
 */
@Service
public class HrmsSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HrmsSyncService.class);
    private static final long OVERLAP_SECONDS = 60L;

    private final HrmsEmployeeAdapter hrms;
    private final UserLifecycleService userLifecycle;
    private final SyncJobRepository jobs;
    private final DcAuditRecorder audit;
    private final TimeSource time;
    private final TransactionTemplate newTx;
    private final boolean scheduledEnabled;
    private final String tenantIds;

    public HrmsSyncService(HrmsEmployeeAdapter hrms, UserLifecycleService userLifecycle, SyncJobRepository jobs,
                           DcAuditRecorder audit, TimeSource time, @Qualifier("dcNewTxTemplate") TransactionTemplate newTx,
                           @Value("${dc.hrms.sync.enabled:false}") boolean scheduledEnabled,
                           @Value("${dc.hrms.sync.tenant-ids:}") String tenantIds) {
        this.hrms = hrms;
        this.userLifecycle = userLifecycle;
        this.jobs = jobs;
        this.audit = audit;
        this.time = time;
        this.newTx = newTx;
        this.scheduledEnabled = scheduledEnabled;
        this.tenantIds = tenantIds;
    }

    @Scheduled(fixedDelayString = "${dc.hrms.sync.interval-ms:300000}", initialDelayString = "${dc.hrms.sync.interval-ms:300000}")
    public void scheduledPull() {
        if (!scheduledEnabled || tenantIds == null || tenantIds.trim().isEmpty()) {
            return;
        }
        for (String raw : tenantIds.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                pull(UUID.fromString(trimmed), DcConstants.SYSTEM_ACTOR);
            } catch (RuntimeException e) {
                LOGGER.error("Scheduled HRMS pull failed for tenant {}", trimmed, e);
            }
        }
    }

    /** Runs one HRMS pull for a tenant and records it as a dc_sync_job (source HRMS_PULL). */
    public DcSyncJob pull(UUID tenantId, UUID actorId) {
        final Instant started = time.now();
        Optional<DcSyncJob> last = jobs.findFirstByTenantIdAndSourceAndStatusOrderByStartedAtDesc(
                tenantId, DcConstants.SYNC_SOURCE_HRMS_PULL, DirectorySyncOrchestrator.STATUS_COMPLETED);
        Instant since = last.isPresent() ? last.get().getStartedAt().minusSeconds(OVERLAP_SECONDS) : null;

        DcSyncJob job = new DcSyncJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenantId);
        job.setSource(DcConstants.SYNC_SOURCE_HRMS_PULL);
        job.setEntityType("EMPLOYEE");
        job.setOperation("PULL");
        job.setIdempotencyKey("HRMS_PULL:" + job.getId());
        job.setStatus("RUNNING");
        job.setStartedAt(started);
        job.setRecordsProcessed(0);
        job.setAttempts(0);
        job.setRequestedBy(actorId);
        saveInNewTx(job); // visible as RUNNING immediately

        int processed = 0;
        int errors = 0;
        StringBuilder errorSummary = new StringBuilder();
        String finalStatus;
        try {
            List<HrmsEmployee> employees = hrms.fetchChangedSince(tenantId, since);
            for (HrmsEmployee e : employees) {
                try {
                    userLifecycle.applyHrmsSnapshot(tenantId, actorId, e);
                    processed++;
                } catch (DcException ex) {
                    errors++;
                    appendError(errorSummary, e.getHrmsEmployeeId() + ": " + ex.getCode());
                } catch (RuntimeException ex) {
                    errors++;
                    LOGGER.error("HRMS employee {} could not be applied", e.getHrmsEmployeeId(), ex);
                    appendError(errorSummary, e.getHrmsEmployeeId() + ": " + ex.getClass().getSimpleName());
                }
            }
            finalStatus = errors == 0 ? DirectorySyncOrchestrator.STATUS_COMPLETED : "COMPLETED_WITH_ERRORS";
        } catch (RuntimeException ex) {
            LOGGER.error("HRMS fetch failed for tenant {} - sync job marked FAILED", tenantId, ex);
            finalStatus = "FAILED";
            appendError(errorSummary, "HRMS fetch failed: " + ex.getClass().getSimpleName());
        }
        job.setStatus(finalStatus);
        job.setRecordsProcessed(processed);
        job.setErrorSummary(errorSummary.length() == 0 ? null : errorSummary.toString());
        job.setCompletedAt(time.now());
        saveInNewTx(job);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("jobId", job.getId().toString());
        payload.put("processed", processed);
        payload.put("errors", errors);
        audit.record(tenantId, actorId, "HRMS_PULL_" + finalStatus, "SYNC_JOB", job.getId().toString(),
                "FAILED".equals(finalStatus) ? "FAILURE" : "SUCCESS", payload);
        return job;
    }

    private void saveInNewTx(DcSyncJob job) {
        newTx.execute(status -> {
            jobs.save(job);
            return null;
        });
    }

    private void appendError(StringBuilder sb, String message) {
        if (sb.length() < 900) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(message);
        }
    }
}
