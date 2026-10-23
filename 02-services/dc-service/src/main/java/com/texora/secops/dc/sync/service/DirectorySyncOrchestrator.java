package com.texora.secops.dc.sync.service;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcGroupMembership;
import com.texora.secops.dc.domain.DcSyncJob;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.infra.AsyncConfig;
import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.SyncJobRepository;
import com.texora.secops.dc.repository.SyncJobSweepRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.sync.client.LdapControlPlaneClient;
import com.texora.secops.dc.sync.client.SyncEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * DC -> LDAP directory synchronization (DC-F-009, LLD 7.1).
 *
 * <ul>
 *   <li><b>Durable intent first:</b> {@link #requestSync} inserts a PENDING dc_sync_job in the caller's transaction,
 *       so a committed user/group change can never lose its sync request.</li>
 *   <li><b>Idempotent:</b> unique (tenant_id, idempotency_key) - the same logical mutation yields one job; the Kafka
 *       messageId is the job id and is stable across retries (the consumer de-duplicates on it).</li>
 *   <li><b>Signed:</b> every publish builds a fresh signed envelope (new issuedAt/nonce) - see LdapControlPlaneClient.</li>
 *   <li><b>Retry + DLQ:</b> exponential backoff up to max-attempts, then dc.sync.requested.dlq + DEAD_LETTERED + ALERT audit.</li>
 * </ul>
 * dc_sync_job.status SENT means "acknowledged by the Kafka broker". There is no application-level ack from LDAP
 * in the LLD, so SENT does not mean "applied in the directory".
 */
@Service
public class DirectorySyncOrchestrator {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DEAD_LETTERED = "DEAD_LETTERED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorySyncOrchestrator.class);
    private static final long LEASE_SECONDS = 60L;

    private final SyncJobRepository jobs;
    private final SyncJobSweepRepository sweepJobs;
    private final LdapControlPlaneClient ldap;
    private final UserRepository users;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final DcAuditRecorder audit;
    private final TimeSource time;
    private final TransactionTemplate newTx;
    private final Executor publishExecutor;
    private final int maxAttempts;
    private final long backoffBaseSeconds;
    private final long backoffMaxSeconds;
    private final long initialDelaySeconds;
    private final int batchSize;

    public DirectorySyncOrchestrator(SyncJobRepository jobs, SyncJobSweepRepository sweepJobs, LdapControlPlaneClient ldap,
                                     UserRepository users, GroupRepository groups, MembershipRepository memberships,
                                     DcAuditRecorder audit, TimeSource time,
                                     @Qualifier("dcNewTxTemplate") TransactionTemplate newTx,
                                     @Qualifier(AsyncConfig.DC_PUBLISH_EXECUTOR) Executor publishExecutor,
                                     @Value("${dc.sync.max-attempts:5}") int maxAttempts,
                                     @Value("${dc.sync.backoff-base-seconds:5}") long backoffBaseSeconds,
                                     @Value("${dc.sync.backoff-max-seconds:600}") long backoffMaxSeconds,
                                     @Value("${dc.sync.initial-delay-seconds:30}") long initialDelaySeconds,
                                     @Value("${dc.sync.retry-batch-size:100}") int batchSize) {
        this.jobs = jobs;
        this.sweepJobs = sweepJobs;
        this.ldap = ldap;
        this.users = users;
        this.groups = groups;
        this.memberships = memberships;
        this.audit = audit;
        this.time = time;
        this.newTx = newTx;
        this.publishExecutor = publishExecutor;
        this.maxAttempts = maxAttempts;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.backoffMaxSeconds = backoffMaxSeconds;
        this.initialDelaySeconds = initialDelaySeconds;
        this.batchSize = batchSize;
    }

    /**
     * Records a sync request. Joins the caller's transaction; publishes after it commits. Returns the existing
     * job if the same idempotency key was already recorded (replay-safe).
     */
    @Transactional
    public DcSyncJob requestSync(UUID tenantId, String entityType, String entityId, String operation,
                                 String idempotencyKey, UUID requestedBy, Map<String, Object> payload) {
        Optional<DcSyncJob> existing = jobs.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = time.now();
        DcSyncJob job = new DcSyncJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenantId);
        job.setSource(DcConstants.SYNC_SOURCE_LDAP_PUSH);
        job.setEntityType(entityType);
        job.setEntityId(entityId);
        job.setOperation(operation);
        job.setIdempotencyKey(idempotencyKey);
        job.setStatus(STATUS_PENDING);
        job.setStartedAt(now);
        job.setRecordsProcessed(0);
        job.setAttempts(0);
        // Grace period so the sweeper does not race the immediate after-commit publish.
        job.setNextRetryAt(now.plusSeconds(initialDelaySeconds));
        job.setRequestedBy(requestedBy);
        job.setPayload(payload);
        jobs.save(job);

        final UUID jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishExecutor.execute(() -> publishJob(tenantId, jobId));
                }
            });
        } else {
            publishExecutor.execute(() -> publishJob(tenantId, jobId));
        }
        return job;
    }

    /** POST /api/v1/directory-synchronization (LDAP_PUSH): enqueue a full re-push of the tenant's users, groups, memberships. */
    @Transactional
    public DcSyncJob triggerFullSync(CallerContext caller) {
        UUID tenantId = caller.getTenantId();
        Instant now = time.now();
        DcSyncJob parent = new DcSyncJob();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setSource(DcConstants.SYNC_SOURCE_LDAP_PUSH);
        parent.setEntityType("FULL_SYNC");
        parent.setOperation("FULL_SYNC");
        parent.setIdempotencyKey("FULL_SYNC:" + parent.getId());
        parent.setStatus(STATUS_COMPLETED);
        parent.setStartedAt(now);
        parent.setRequestedBy(caller.getSubjectId());
        int count = 0;

        List<DcUser> allUsers = users.findByTenantId(tenantId);
        Map<UUID, DcUser> usersById = new HashMap<UUID, DcUser>();
        for (DcUser u : allUsers) {
            usersById.put(u.getId(), u);
            requestSync(tenantId, "USER", u.getId().toString(), "UPSERT", "FULLSYNC:" + parent.getId() + ":USER:" + u.getId(),
                    caller.getSubjectId(), SyncPayloads.user(u));
            count++;
        }
        Map<UUID, DcGroup> groupsById = new HashMap<UUID, DcGroup>();
        for (DcGroup g : groups.findByTenantId(tenantId)) {
            groupsById.put(g.getId(), g);
            requestSync(tenantId, "GROUP", g.getId().toString(), "UPSERT", "FULLSYNC:" + parent.getId() + ":GROUP:" + g.getId(),
                    caller.getSubjectId(), SyncPayloads.group(g));
            count++;
        }
        for (DcGroupMembership m : memberships.findAllByTenant(tenantId)) {
            DcGroup g = groupsById.get(m.getId().getGroupId());
            DcUser u = usersById.get(m.getId().getUserId());
            if (g != null && u != null) {
                requestSync(tenantId, "GROUP_MEMBERSHIP", g.getId() + ":" + u.getId(), "ADD",
                        "FULLSYNC:" + parent.getId() + ":MEMBERSHIP:" + g.getId() + ":" + u.getId(),
                        caller.getSubjectId(), SyncPayloads.membership(g, u));
                count++;
            }
        }
        parent.setRecordsProcessed(count); // number of messages ENQUEUED (delivery is tracked per child job)
        parent.setCompletedAt(time.now());
        jobs.save(parent);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("jobId", parent.getId().toString());
        payload.put("enqueued", count);
        audit.record(tenantId, caller.getSubjectId(), "DIRECTORY_SYNC_TRIGGERED", "SYNC_JOB", parent.getId().toString(), "SUCCESS", payload);
        return parent;
    }

    /** Publishes one job. Safe to call repeatedly: only PENDING jobs are published. Called off the request thread. */
    public void publishJob(UUID tenantId, UUID jobId) {
        DcSyncJob job = jobs.findByTenantIdAndId(tenantId, jobId).orElse(null);
        if (job == null || !STATUS_PENDING.equals(job.getStatus())) {
            return;
        }
        SyncEnvelope envelope = null;
        try {
            envelope = ldap.toEnvelope(job);
            ldap.publish(envelope);
            markSent(tenantId, jobId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(tenantId, job, envelope, e);
        } catch (Exception e) {
            handleFailure(tenantId, job, envelope, e);
        }
    }

    /** Retries PENDING jobs whose next_retry_at has passed (covers lost after-commit publishes and failures). */
    @Scheduled(fixedDelayString = "${dc.sync.retry-interval-ms:10000}")
    public void retryDue() {
        final Instant now = time.now();
        List<DcSyncJob> claimed = newTx.execute(status -> {
            List<DcSyncJob> due = sweepJobs.claimDueAcrossTenants(now, batchSize);
            List<DcSyncJob> copy = new ArrayList<DcSyncJob>();
            for (DcSyncJob j : due) {
                j.setNextRetryAt(now.plusSeconds(LEASE_SECONDS)); // lease
                sweepJobs.save(j);
                copy.add(j);
            }
            return copy;
        });
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        LOGGER.info("Retrying {} pending directory-sync jobs", claimed.size());
        for (DcSyncJob j : claimed) {
            try {
                publishJob(j.getTenantId(), j.getId());
            } catch (RuntimeException e) {
                LOGGER.error("Sync retry crashed for job {}", j.getId(), e);
            }
        }
    }

    public long backoffSeconds(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        return Math.min(backoffMaxSeconds, backoffBaseSeconds * multiplier);
    }

    private void markSent(UUID tenantId, UUID jobId) {
        newTx.execute(status -> {
            DcSyncJob j = jobs.findByTenantIdAndId(tenantId, jobId).orElse(null);
            if (j != null) {
                j.setStatus(STATUS_SENT);
                j.setAttempts(j.getAttempts() + 1);
                j.setRecordsProcessed(1);
                j.setCompletedAt(time.now());
                j.setNextRetryAt(null);
                j.setErrorSummary(null);
                jobs.save(j);
            }
            return null;
        });
    }

    private void handleFailure(UUID tenantId, DcSyncJob job, SyncEnvelope envelope, Exception cause) {
        int attempts = job.getAttempts() + 1;
        String error = String.valueOf(cause);
        if (error.length() > 1000) {
            error = error.substring(0, 1000);
        }
        boolean exhausted = attempts >= maxAttempts;
        boolean deadLettered = false;
        if (exhausted && envelope != null) {
            try {
                ldap.publishToDeadLetter(envelope, error);
                deadLettered = true;
            } catch (Exception dlqFailure) {
                // DLQ unreachable: do NOT drop the message - keep it PENDING and retry at max backoff.
                LOGGER.error("DLQ publish failed for sync job {} - keeping PENDING", job.getId(), dlqFailure);
            }
        }
        final boolean dead = deadLettered;
        final String summary = error;
        final int attemptCount = attempts;
        final long delay = exhausted ? backoffMaxSeconds : backoffSeconds(attempts);
        newTx.execute(status -> {
            DcSyncJob j = jobs.findByTenantIdAndId(tenantId, job.getId()).orElse(null);
            if (j != null) {
                j.setAttempts(attemptCount);
                j.setErrorSummary(summary);
                if (dead) {
                    j.setStatus(STATUS_DEAD_LETTERED);
                    j.setCompletedAt(time.now());
                    j.setNextRetryAt(null);
                } else {
                    j.setNextRetryAt(time.now().plusSeconds(delay));
                }
                jobs.save(j);
            }
            return null;
        });
        LOGGER.warn("Directory sync publish failed job={} attempt={} deadLettered={}", job.getId(), attempts, dead);
        if (dead) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("jobId", job.getId().toString());
            payload.put("entityType", job.getEntityType());
            payload.put("attempts", attemptCount);
            try {
                audit.recordIndependent(tenantId, DcConstants.SYSTEM_ACTOR, "SYNC_DEAD_LETTERED", "SYNC_JOB",
                        job.getId().toString(), "ALERT", payload);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to audit dead-lettered sync job {}", job.getId(), e);
            }
        }
    }
}
