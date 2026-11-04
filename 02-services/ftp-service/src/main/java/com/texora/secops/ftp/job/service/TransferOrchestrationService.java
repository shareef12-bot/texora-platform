package com.texora.secops.ftp.job.service;

import com.texora.secops.ftp.admin.service.ServiceConfigService;
import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.checksum.service.ChecksumVerificationService;
import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.domain.AttemptOutcome;
import com.texora.secops.ftp.domain.EndpointDirection;
import com.texora.secops.ftp.domain.FtpEndpoint;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.JobStatuses;
import com.texora.secops.ftp.domain.TransferAttempt;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.domain.TransferPhase;
import com.texora.secops.ftp.endpoint.service.EndpointAllowlistService;
import com.texora.secops.ftp.exception.FtpException;
import com.texora.secops.ftp.exception.UpstreamUnavailableException;
import com.texora.secops.ftp.quarantine.service.QuarantineService;
import com.texora.secops.ftp.repository.TransferAttemptRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.TenantBinder;
import com.texora.secops.ftp.security.TransferCredential;
import com.texora.secops.ftp.security.TransferCredentialClient;
import com.texora.secops.ftp.transfer.client.FetchResult;
import com.texora.secops.ftp.transfer.client.SecureTransferEngineAdapter;
import com.texora.secops.ftp.transfer.client.TransferTarget;
import com.texora.secops.ftp.transfer.client.TransferTransportException;
import com.texora.secops.ftp.transfer.storage.StagingStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives one transfer job end to end:
 *
 *   allowlist re-check -> FETCH (retry/resume, one ftp_transfer_attempt row per try) -> promote + re-hash ->
 *   idempotent checksum record/verify -> inspection (fail-closed quarantine) -> RELEASE to destination -> COMPLETED
 *
 * Bytes are only ever delivered to the destination AFTER checksum verification and, when required, an explicit CLEAN
 * verdict. This class is NOT transactional as a whole: every database step is its own short transaction so no
 * connection is held across network I/O. It never touches SFTP/FTPS directly (only via SecureTransferEngineAdapter)
 * and never opens, executes or interprets file content.
 *
 * Every state change is a compare-and-set ({@code jobs.transition}) so a concurrent cancel/quarantine always wins and
 * this worker stops instead of overwriting it.
 */
@Service
public class TransferOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferOrchestrationService.class);

    @FunctionalInterface
    private interface TransportOp {
        void run() throws TransferTransportException;
    }

    private final TransferJobRepository jobs;
    private final TransferAttemptRepository attempts;
    private final EndpointAllowlistService endpoints;
    private final SecureTransferEngineAdapter adapter;
    private final TransferCredentialClient credentials;
    private final StagingStorage staging;
    private final ChecksumVerificationService checksums;
    private final QuarantineService quarantine;
    private final ServiceConfigService config;
    private final FtpAuditTrail audit;
    private final FtpProperties props;
    private final TransactionTemplate tx;

    public TransferOrchestrationService(TransferJobRepository jobs, TransferAttemptRepository attempts,
                                        EndpointAllowlistService endpoints, SecureTransferEngineAdapter adapter,
                                        TransferCredentialClient credentials, StagingStorage staging,
                                        ChecksumVerificationService checksums, QuarantineService quarantine,
                                        ServiceConfigService config, FtpAuditTrail audit, FtpProperties props,
                                        PlatformTransactionManager tm) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.endpoints = endpoints;
        this.adapter = adapter;
        this.credentials = credentials;
        this.staging = staging;
        this.checksums = checksums;
        this.quarantine = quarantine;
        this.config = config;
        this.audit = audit;
        this.props = props;
        this.tx = new TransactionTemplate(tm);
    }

    // =====================================================================================================
    // Entry points (called from TransferEventListener on an executor thread)
    // =====================================================================================================

    public void run(UUID jobId, UUID tenantId) {
        bindSystem(tenantId);
        try {
            execute(jobId, tenantId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error running transfer job {}", jobId, e);
            failSafely(jobId, tenantId, "INTERNAL_ERROR");
        } finally {
            unbind();
        }
    }

    /** Delivers a file a second person has released from quarantine. */
    public void deliverReleased(UUID jobId, UUID tenantId) {
        bindSystem(tenantId);
        try {
            executeRelease(jobId, tenantId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error delivering released job {}", jobId, e);
            failSafely(jobId, tenantId, "INTERNAL_ERROR");
        } finally {
            unbind();
        }
    }

    // =====================================================================================================
    // Main flow
    // =====================================================================================================

    private void execute(UUID jobId, UUID tenantId) {
        final TransferJob job = jobs.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.SUBMITTED) {
            LOGGER.warn("Job {} not found or not SUBMITTED; nothing to do", jobId);
            return;
        }

        // Re-check at execution time: the endpoint may have been de-allowlisted since submission.
        final FtpEndpoint source = loadEndpoint(job, job.getSourceEndpointId(), EndpointDirection.SOURCE);
        final FtpEndpoint dest = loadEndpoint(job, job.getDestEndpointId(), EndpointDirection.DEST);
        if (source == null || dest == null) {
            fail(job, "ENDPOINT_NOT_ALLOWLISTED");
            return;
        }

        final int maxAttempts = config.maxAttempts(tenantId);
        final long maxBytes = config.maxFileBytes(tenantId);
        final AtomicInteger counter = new AtomicInteger(attempts.maxAttemptNumber(jobId, tenantId));

        final Path part;
        try {
            part = staging.partFile(tenantId, jobId);
        } catch (IOException e) {
            LOGGER.error("Cannot prepare staging for job {}", jobId, e);
            fail(job, "STAGING_ERROR");
            return;
        }

        // ---- Phase 1: FETCH with retry/resume ----
        final TransferTarget sourceTarget = toTarget(source);
        final FetchResult[] fetched = new FetchResult[1];
        boolean fetchedOk = runWithRetry(job, TransferPhase.FETCH, JobStatus.IN_PROGRESS, maxAttempts, counter, () -> {
            TransferCredential credential = credentialFor(source, tenantId);
            fetched[0] = adapter.fetchToStaging(sourceTarget, credential, job.getFileName(), part, maxBytes,
                    () -> notRunning(jobId, tenantId));
        });
        if (!fetchedOk) {
            return;
        }

        // ---- Phase 2: promote, independent re-hash, idempotent checksum record ----
        final Path staged;
        final String computed;
        try {
            staged = staging.promote(tenantId, jobId);
            computed = staging.sha256(staged);
        } catch (IOException e) {
            LOGGER.error("Cannot finalise staged file for job {}", jobId, e);
            fail(job, "STAGING_ERROR");
            return;
        }
        recordStagingUri(job, staging.uriFor(tenantId, jobId));
        final String expected = job.getExpectedSha256() != null ? job.getExpectedSha256() : fetched[0].getSha256();
        boolean verified;
        try {
            verified = checksums.recordAndVerify(tenantId, CallerAuthentication.SYSTEM_ACTOR, jobId,
                    ChecksumVerificationService.SHA256, expected, computed);
        } catch (FtpException e) {
            LOGGER.error("Checksum conflict for job {}: {}", jobId, e.getCode());
            fail(job, "CHECKSUM_CONFLICT");
            return;
        }
        if (!verified) {
            fail(job, "CHECKSUM_MISMATCH");
            return;
        }

        // ---- Phase 3: fail-closed inspection ----
        if (job.isRequireInspection()) {
            if (!quarantine.inspectStaged(job, staged, computed)) {
                return;   // QUARANTINED (or no longer running). File stays staged; NEVER delivered from here.
            }
        } else {
            audit.inspectionResult(tenantId, CallerAuthentication.SYSTEM_ACTOR, jobId, "SKIPPED",
                    Map.<String, Object>of("reason", "inspection not required by tenant policy"));
        }

        // ---- Phase 4: release to the destination ----
        deliver(job, dest, staged, JobStatus.IN_PROGRESS, maxAttempts, counter);
    }

    private void executeRelease(UUID jobId, UUID tenantId) {
        final TransferJob job = jobs.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null) {
            LOGGER.warn("Released job {} not found", jobId);
            return;
        }
        int moved = transition(job, EnumSet.of(JobStatus.QUARANTINED), JobStatus.RESUMED, null);
        if (moved == 0) {
            LOGGER.warn("Job {} is not QUARANTINED; ignoring release event", jobId);
            return;
        }
        final FtpEndpoint dest = loadEndpoint(job, job.getDestEndpointId(), EndpointDirection.DEST);
        if (dest == null) {
            fail(job, "ENDPOINT_NOT_ALLOWLISTED");
            return;
        }
        final Path staged = staging.stagedFile(tenantId, jobId);
        if (!staging.exists(staged)) {
            fail(job, "STAGED_FILE_MISSING");
            return;
        }
        try {
            // Integrity re-check: the bytes must still be exactly what was verified before quarantine.
            String hash = staging.sha256(staged);
            if (!checksums.matchesRecorded(tenantId, jobId, ChecksumVerificationService.SHA256, hash)) {
                fail(job, "STAGED_FILE_INTEGRITY");
                return;
            }
        } catch (IOException e) {
            LOGGER.error("Cannot re-hash staged file for job {}", jobId, e);
            fail(job, "STAGING_ERROR");
            return;
        }
        int maxAttempts = config.maxAttempts(tenantId);
        AtomicInteger counter = new AtomicInteger(attempts.maxAttemptNumber(jobId, tenantId));
        deliver(job, dest, staged, JobStatus.RESUMED, maxAttempts, counter);
    }

    private void deliver(TransferJob job, FtpEndpoint dest, Path staged, JobStatus startStatus, int maxAttempts,
                         AtomicInteger counter) {
        final UUID tenantId = job.getTenantId();
        final UUID jobId = job.getId();
        final TransferTarget destTarget = toTarget(dest);
        boolean delivered = runWithRetry(job, TransferPhase.RELEASE, startStatus, maxAttempts, counter, () -> {
            TransferCredential credential = credentialFor(dest, tenantId);
            adapter.deliverFromStaging(destTarget, credential, job.getFileName(), staged,
                    () -> notRunning(jobId, tenantId));
        });
        if (!delivered) {
            return;
        }
        int moved = transition(job, JobStatuses.RUNNING, JobStatus.COMPLETED, null);
        if (moved > 0) {
            audit.jobCompleted(tenantId, CallerAuthentication.SYSTEM_ACTOR, jobId,
                    Map.<String, Object>of("attempts", counter.get()));
            staging.destroy(tenantId, jobId);
        } else {
            LOGGER.warn("Job {} was delivered but is no longer running; leaving state untouched", jobId);
        }
    }

    // =====================================================================================================
    // Retry / resume engine - one ftp_transfer_attempt row per try
    // =====================================================================================================

    /**
     * @return true if the operation succeeded; false if the job has been failed, cancelled or quarantined and the
     *         caller must stop.
     */
    private boolean runWithRetry(TransferJob job, TransferPhase phase, JobStatus startStatus, int maxAttempts,
                                 AtomicInteger counter, TransportOp op) {
        int tries = 0;
        while (true) {
            tries++;
            final int number = counter.incrementAndGet();
            JobStatus entering = tries == 1 ? startStatus : JobStatus.RESUMED;
            UUID attemptId = beginAttempt(job, number, phase, entering);
            if (attemptId == null) {
                LOGGER.info("Job {} is no longer running; stopping before attempt {}", job.getId(), number);
                return false;
            }
            try {
                op.run();
                endAttempt(job, attemptId, number, phase, AttemptOutcome.SUCCESS, null);
                return true;
            } catch (TransferTransportException e) {
                if (e.isAborted()) {
                    endAttempt(job, attemptId, number, phase, AttemptOutcome.CANCELLED, "Aborted: job no longer running");
                    cleanupIfFailed(job);
                    return false;
                }
                boolean retry = e.isRetriable() && tries < maxAttempts;
                String summary = e.getCode() + ": " + e.getMessage();
                endAttempt(job, attemptId, number, phase,
                        retry ? AttemptOutcome.FAILED_RETRYABLE : AttemptOutcome.FAILED_FATAL, summary);
                if (!retry) {
                    fail(job, e.isRetriable() ? "RETRIES_EXHAUSTED:" + e.getCode() : e.getCode());
                    return false;
                }
                if (!backoff(tries)) {
                    fail(job, "INTERRUPTED");
                    return false;
                }
            } catch (RuntimeException e) {
                // Keep ftp_transfer_attempt truthful even for bugs: close the row, then let run() fail the job.
                endAttempt(job, attemptId, number, phase, AttemptOutcome.FAILED_FATAL,
                        "INTERNAL_ERROR: " + e.getClass().getSimpleName());
                throw e;
            }
        }
    }

    private UUID beginAttempt(TransferJob job, int number, TransferPhase phase, JobStatus entering) {
        return tx.execute(status -> {
            int moved = jobs.transition(job.getId(), job.getTenantId(), JobStatuses.RUNNING, entering, null, Instant.now());
            if (moved == 0) {
                return null;
            }
            TransferAttempt attempt = new TransferAttempt(UUID.randomUUID(), job.getTenantId(), job.getId(), number, phase,
                    AttemptOutcome.IN_PROGRESS, Instant.now(), null, null);
            attempts.save(attempt);
            return attempt.getId();
        });
    }

    private void endAttempt(TransferJob job, UUID attemptId, int number, TransferPhase phase, AttemptOutcome outcome,
                            String summary) {
        tx.executeWithoutResult(status -> {
            TransferAttempt attempt = attempts.findByIdAndTenantId(attemptId, job.getTenantId()).orElse(null);
            if (attempt == null) {
                LOGGER.error("Attempt {} vanished for job {}", attemptId, job.getId());
                return;
            }
            attempt.setOutcome(outcome);
            attempt.setEndedAt(Instant.now());
            attempt.setErrorSummary(summary == null ? null : (summary.length() > 1000 ? summary.substring(0, 1000) : summary));
            attempts.save(attempt);
            audit.transferAttempt(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, job.getId(), Map.<String, Object>of(
                    "attempt", number, "phase", phase.name(), "outcome", outcome.name()));
        });
    }

    private boolean backoff(int tries) {
        long millis = props.getTransfer().getRetryBackoffMs() * tries;
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // =====================================================================================================
    // Helpers
    // =====================================================================================================

    private FtpEndpoint loadEndpoint(TransferJob job, UUID endpointId, EndpointDirection direction) {
        try {
            return endpoints.requireAllowlisted(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, endpointId, direction);
        } catch (FtpException e) {
            LOGGER.warn("Endpoint {} rejected for job {}: {}", endpointId, job.getId(), e.getCode());
            return null;
        }
    }

    private TransferCredential credentialFor(FtpEndpoint endpoint, UUID tenantId) throws TransferTransportException {
        try {
            return credentials.fetch(tenantId, endpoint.getCredentialRef());
        } catch (UpstreamUnavailableException e) {
            throw TransferTransportException.retriable("CREDENTIAL_UNAVAILABLE", "Credential service unavailable", e);
        }
    }

    private static TransferTarget toTarget(FtpEndpoint e) {
        return new TransferTarget(e.getProtocol().name(), e.getHost(), e.getPort(), e.getBasePath(), e.getHostKeyFingerprint());
    }

    private boolean notRunning(UUID jobId, UUID tenantId) {
        return !jobs.existsByIdAndTenantIdAndStatusIn(jobId, tenantId, JobStatuses.RUNNING);
    }

    private int transition(TransferJob job, Set<JobStatus> from, JobStatus to, String reason) {
        Integer moved = tx.execute(status -> jobs.transition(job.getId(), job.getTenantId(), from, to, reason, Instant.now()));
        return moved == null ? 0 : moved;
    }

    private void recordStagingUri(TransferJob job, String uri) {
        tx.executeWithoutResult(status -> jobs.findByIdAndTenantId(job.getId(), job.getTenantId()).ifPresent(j -> {
            j.setStagingUri(uri);
            jobs.save(j);
        }));
    }

    /** RUNNING -> FAILED. Staging is removed only if THIS call actually failed the job (a concurrent quarantine keeps it). */
    private void fail(TransferJob job, String reason) {
        int moved = transition(job, JobStatuses.RUNNING, JobStatus.FAILED, reason);
        if (moved > 0) {
            audit.jobFailed(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, job.getId(), reason);
            staging.destroy(job.getTenantId(), job.getId());
        }
    }

    private void failSafely(UUID jobId, UUID tenantId, String reason) {
        try {
            TransferJob job = jobs.findByIdAndTenantId(jobId, tenantId).orElse(null);
            if (job != null) {
                fail(job, reason);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Could not mark job {} as failed", jobId, e);
        }
    }

    private void cleanupIfFailed(TransferJob job) {
        TransferJob current = jobs.findByIdAndTenantId(job.getId(), job.getTenantId()).orElse(null);
        if (current != null && current.getStatus() == JobStatus.FAILED) {
            staging.destroy(job.getTenantId(), job.getId());
        }
    }

    private static void bindSystem(UUID tenantId) {
        SecurityContextHolder.getContext().setAuthentication(CallerAuthentication.system(tenantId));
        TenantBinder.bind(tenantId);
    }

    private static void unbind() {
        SecurityContextHolder.clearContext();
        TenantBinder.clear();
    }
}
