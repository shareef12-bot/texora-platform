package com.texora.secops.ftp.job;

import com.texora.secops.ftp.admin.service.ServiceConfigService;
import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.checksum.service.ChecksumVerificationService;
import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.domain.AttemptOutcome;
import com.texora.secops.ftp.domain.EndpointDirection;
import com.texora.secops.ftp.domain.FtpEndpoint;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.TransferAttempt;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.domain.TransferPhase;
import com.texora.secops.ftp.domain.TransferProtocol;
import com.texora.secops.ftp.endpoint.service.EndpointAllowlistService;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.job.service.TransferOrchestrationService;
import com.texora.secops.ftp.quarantine.service.QuarantineService;
import com.texora.secops.ftp.repository.TransferAttemptRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.TransferCredential;
import com.texora.secops.ftp.security.TransferCredentialClient;
import com.texora.secops.ftp.transfer.client.FetchResult;
import com.texora.secops.ftp.transfer.client.SecureTransferEngineAdapter;
import com.texora.secops.ftp.transfer.client.TransferTransportException;
import com.texora.secops.ftp.transfer.storage.StagingStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resilience tests: resume/retry under simulated network interruption, and that ftp_transfer_attempt records the
 * FULL retry history. The adapter is mocked to inject failures; the real orchestration logic runs.
 */
class TransferOrchestrationServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID srcId = UUID.randomUUID();
    private final UUID dstId = UUID.randomUUID();
    private final Path part = Path.of("payload.part");
    private final Path staged = Path.of("payload.staged");

    private final TransferJobRepository jobs = mock(TransferJobRepository.class);
    private final TransferAttemptRepository attempts = mock(TransferAttemptRepository.class);
    private final EndpointAllowlistService endpoints = mock(EndpointAllowlistService.class);
    private final SecureTransferEngineAdapter adapter = mock(SecureTransferEngineAdapter.class);
    private final TransferCredentialClient credentials = mock(TransferCredentialClient.class);
    private final StagingStorage staging = mock(StagingStorage.class);
    private final ChecksumVerificationService checksums = mock(ChecksumVerificationService.class);
    private final QuarantineService quarantine = mock(QuarantineService.class);
    private final ServiceConfigService config = mock(ServiceConfigService.class);
    private final FtpAuditTrail audit = mock(FtpAuditTrail.class);
    private final Map<UUID, TransferAttempt> attemptStore = new HashMap<>();

    private TransferOrchestrationService service;
    private TransferJob job;

    @BeforeEach
    void setUp() throws Exception {
        FtpProperties props = new FtpProperties();
        props.getTransfer().setRetryBackoffMs(0);
        service = new TransferOrchestrationService(jobs, attempts, endpoints, adapter, credentials, staging, checksums,
                quarantine, config, audit, props, mock(PlatformTransactionManager.class));

        job = newJob(JobStatus.SUBMITTED, true);
        when(jobs.findByIdAndTenantId(jobId, tenant)).thenAnswer(inv -> Optional.of(job));
        when(jobs.transition(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jobs.existsByIdAndTenantIdAndStatusIn(any(), any(), any())).thenReturn(true);

        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(srcId), eq(EndpointDirection.SOURCE)))
                .thenReturn(endpoint(srcId, EndpointDirection.SOURCE));
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(dstId), eq(EndpointDirection.DEST)))
                .thenReturn(endpoint(dstId, EndpointDirection.DEST));
        when(config.maxAttempts(tenant)).thenReturn(3);
        when(config.maxFileBytes(tenant)).thenReturn(1_000_000L);
        when(attempts.maxAttemptNumber(jobId, tenant)).thenReturn(0);
        when(attempts.save(any(TransferAttempt.class))).thenAnswer(inv -> {
            TransferAttempt a = inv.getArgument(0);
            attemptStore.put(a.getId(), a);
            return a;
        });
        when(attempts.findByIdAndTenantId(any(), any())).thenAnswer(inv -> Optional.ofNullable(attemptStore.get(inv.<UUID>getArgument(0))));
        when(credentials.fetch(any(), any())).thenReturn(new TransferCredential("user", "pw", null));

        when(staging.partFile(tenant, jobId)).thenReturn(part);
        when(staging.promote(tenant, jobId)).thenReturn(staged);
        when(staging.sha256(staged)).thenReturn("abc");
        when(staging.uriFor(tenant, jobId)).thenReturn("file:/x");
        when(staging.stagedFile(tenant, jobId)).thenReturn(staged);
        when(checksums.recordAndVerify(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(quarantine.inspectStaged(any(), any(), any())).thenReturn(true);
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any())).thenReturn(new FetchResult(10, "abc", 0));
    }

    private TransferJob newJob(JobStatus status, boolean inspect) {
        return new TransferJob(jobId, tenant, UUID.randomUUID(), srcId, dstId, "payroll.csv", inspect, null, null, status,
                null, Instant.now(), Instant.now());
    }

    private FtpEndpoint endpoint(UUID id, EndpointDirection d) {
        return new FtpEndpoint(id, tenant, "sftp.example.com", 22, d, TransferProtocol.SFTP, "/", "ref", "SHA256:x", true,
                UUID.randomUUID(), Instant.now());
    }

    private List<TransferAttempt> history() {
        return attemptStore.values().stream().sorted((a, b) -> Integer.compare(a.getAttemptNumber(), b.getAttemptNumber()))
                .collect(Collectors.toList());
    }

    private String failedReason() {
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(jobs).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.FAILED), reason.capture(), any());
        return reason.getValue();
    }

    @Test
    void networkInterruptionsAreRetriedAndTheFullHistoryIsRecorded() throws Exception {
        TransferTransportException drop = TransferTransportException.retriable("TRANSPORT_ERROR", "connection reset", null);
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(drop).thenThrow(drop).thenReturn(new FetchResult(10, "abc", 4));

        service.run(jobId, tenant);

        List<TransferAttempt> h = history();
        assertThat(h).hasSize(4);
        assertThat(h.get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(h.get(0).getPhase()).isEqualTo(TransferPhase.FETCH);
        assertThat(h.get(0).getOutcome()).isEqualTo(AttemptOutcome.FAILED_RETRYABLE);
        assertThat(h.get(0).getErrorSummary()).contains("TRANSPORT_ERROR");
        assertThat(h.get(1).getOutcome()).isEqualTo(AttemptOutcome.FAILED_RETRYABLE);
        assertThat(h.get(2).getPhase()).isEqualTo(TransferPhase.FETCH);
        assertThat(h.get(2).getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(h.get(3).getPhase()).isEqualTo(TransferPhase.RELEASE);
        assertThat(h.get(3).getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(h).allSatisfy(a -> assertThat(a.getEndedAt()).isNotNull());

        // resumed attempts move the job to RESUMED; it finishes COMPLETED
        verify(jobs, times(1)).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.COMPLETED), any(), any());
        verify(jobs, times(2)).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.RESUMED), any(), any());
        // exactly ONE checksum recording however many retries happened
        verify(checksums, times(1)).recordAndVerify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retriesAreBoundedAndThenTheJobFails() throws Exception {
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(TransferTransportException.retriable("TRANSPORT_ERROR", "reset", null));

        service.run(jobId, tenant);

        List<TransferAttempt> h = history();
        assertThat(h).extracting(TransferAttempt::getOutcome).containsExactly(
                AttemptOutcome.FAILED_RETRYABLE, AttemptOutcome.FAILED_RETRYABLE, AttemptOutcome.FAILED_FATAL);
        assertThat(failedReason()).startsWith("RETRIES_EXHAUSTED");
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
        verify(quarantine, never()).inspectStaged(any(), any(), any());
        verify(staging).destroy(tenant, jobId);
    }

    @Test
    void fatalErrorsAreNotRetried() throws Exception {
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(TransferTransportException.fatal("HOST_KEY_MISMATCH", "pin mismatch", null));

        service.run(jobId, tenant);

        assertThat(history()).hasSize(1);
        assertThat(history().get(0).getOutcome()).isEqualTo(AttemptOutcome.FAILED_FATAL);
        assertThat(failedReason()).isEqualTo("HOST_KEY_MISMATCH");
        verify(adapter, times(1)).fetchToStaging(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void credentialOutageIsTreatedAsRetriable() throws Exception {
        when(credentials.fetch(any(), any()))
                .thenThrow(new com.texora.secops.ftp.exception.UpstreamUnavailableException("CREDENTIAL_UNAVAILABLE", "sec down"))
                .thenReturn(new TransferCredential("user", "pw", null));
        service.run(jobId, tenant);
        assertThat(history().get(0).getOutcome()).isEqualTo(AttemptOutcome.FAILED_RETRYABLE);
        assertThat(history().get(1).getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void quarantinedFileIsNeverDelivered() throws Exception {
        when(quarantine.inspectStaged(any(), any(), any())).thenReturn(false);

        service.run(jobId, tenant);

        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
        verify(jobs, never()).transition(any(), any(), any(), eq(JobStatus.COMPLETED), any(), any());
        verify(staging, never()).destroy(any(), any());      // the quarantined payload is retained for review
        assertThat(history()).hasSize(1);                     // FETCH only
    }

    @Test
    void checksumMismatchFailsTheJobBeforeInspectionOrDelivery() throws Exception {
        when(checksums.recordAndVerify(any(), any(), any(), any(), any(), any())).thenReturn(false);

        service.run(jobId, tenant);

        assertThat(failedReason()).isEqualTo("CHECKSUM_MISMATCH");
        verify(quarantine, never()).inspectStaged(any(), any(), any());
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
    }

    @Test
    void checksumConflictFailsTheJob() {
        when(checksums.recordAndVerify(any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.texora.secops.ftp.exception.ConflictException("CHECKSUM_CONFLICT", "differs"));
        service.run(jobId, tenant);
        assertThat(failedReason()).isEqualTo("CHECKSUM_CONFLICT");
    }

    @Test
    void endpointDeAllowlistedBeforeExecutionIsNeverContacted() throws Exception {
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(srcId), eq(EndpointDirection.SOURCE)))
                .thenThrow(new UnprocessableException("ENDPOINT_NOT_ALLOWLISTED", "no"));

        service.run(jobId, tenant);

        assertThat(failedReason()).isEqualTo("ENDPOINT_NOT_ALLOWLISTED");
        verify(adapter, never()).fetchToStaging(any(), any(), any(), any(), anyLong(), any());
        verify(credentials, never()).fetch(any(), any());
    }

    @Test
    void cancelDuringTransferStopsWithoutFurtherAttempts() throws Exception {
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(TransferTransportException.aborted());

        service.run(jobId, tenant);

        assertThat(history()).hasSize(1);
        assertThat(history().get(0).getOutcome()).isEqualTo(AttemptOutcome.CANCELLED);
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
    }

    @Test
    void jobThatIsNoLongerRunningMakesNoAttempt() throws Exception {
        when(jobs.transition(any(), any(), any(), eq(JobStatus.IN_PROGRESS), any(), any())).thenReturn(0);
        service.run(jobId, tenant);
        assertThat(history()).isEmpty();
        verify(adapter, never()).fetchToStaging(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void jobsThatAreNotSubmittedAreIgnored() throws Exception {
        job = newJob(JobStatus.COMPLETED, true);
        service.run(jobId, tenant);
        verify(adapter, never()).fetchToStaging(any(), any(), any(), any(), anyLong(), any());
        assertThat(history()).isEmpty();
    }

    @Test
    void whenInspectionIsNotRequiredTheFileIsDeliveredAndTheSkipIsAudited() throws Exception {
        job = newJob(JobStatus.SUBMITTED, false);
        service.run(jobId, tenant);
        verify(quarantine, never()).inspectStaged(any(), any(), any());
        verify(audit).inspectionResult(eq(tenant), anyString(), eq(jobId), eq("SKIPPED"), any());
        verify(adapter).deliverFromStaging(any(), any(), eq("payroll.csv"), eq(staged), any());
    }

    @Test
    void unexpectedBugsFailTheJobAndStillCloseTheAttemptRow() throws Exception {
        when(adapter.fetchToStaging(any(), any(), any(), any(), anyLong(), any())).thenThrow(new IllegalStateException("bug"));

        service.run(jobId, tenant);

        assertThat(history()).hasSize(1);
        assertThat(history().get(0).getOutcome()).isEqualTo(AttemptOutcome.FAILED_FATAL);
        assertThat(history().get(0).getErrorSummary()).startsWith("INTERNAL_ERROR");
        assertThat(failedReason()).isEqualTo("INTERNAL_ERROR");
    }

    // ------------------------------------------------------------------ delivery of a file released from quarantine

    @Test
    void releasedFileIsIntegrityCheckedThenDelivered() throws Exception {
        job = newJob(JobStatus.QUARANTINED, true);
        when(staging.exists(staged)).thenReturn(true);
        when(checksums.matchesRecorded(eq(tenant), eq(jobId), anyString(), anyString())).thenReturn(true);

        service.deliverReleased(jobId, tenant);

        verify(adapter).deliverFromStaging(any(), any(), eq("payroll.csv"), eq(staged), any());
        verify(jobs).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.COMPLETED), any(), any());
        assertThat(history().get(0).getPhase()).isEqualTo(TransferPhase.RELEASE);
    }

    @Test
    void releasedFileThatChangedOnDiskIsNotDelivered() throws Exception {
        job = newJob(JobStatus.QUARANTINED, true);
        when(staging.exists(staged)).thenReturn(true);
        when(checksums.matchesRecorded(eq(tenant), eq(jobId), anyString(), anyString())).thenReturn(false);

        service.deliverReleased(jobId, tenant);

        assertThat(failedReason()).isEqualTo("STAGED_FILE_INTEGRITY");
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
    }

    @Test
    void releasedFileMissingFromStagingFailsTheJob() throws Exception {
        job = newJob(JobStatus.QUARANTINED, true);
        when(staging.exists(staged)).thenReturn(false);
        service.deliverReleased(jobId, tenant);
        assertThat(failedReason()).isEqualTo("STAGED_FILE_MISSING");
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
    }

    @Test
    void releaseEventForAJobThatIsNotQuarantinedIsIgnored() throws Exception {
        when(jobs.transition(any(), any(), any(), eq(JobStatus.RESUMED), any(), any())).thenReturn(0);
        service.deliverReleased(jobId, tenant);
        verify(adapter, never()).deliverFromStaging(any(), any(), any(), any(), any());
    }
}
