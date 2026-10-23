package com.texora.secops.ftp.checksum;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.checksum.service.ChecksumVerificationService;
import com.texora.secops.ftp.domain.ChecksumRecord;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.repository.ChecksumRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChecksumVerificationServiceTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String ALGO = ChecksumVerificationService.SHA256;

    private final UUID tenant = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final ChecksumRecordRepository repo = mock(ChecksumRecordRepository.class);
    private final FtpAuditTrail audit = mock(FtpAuditTrail.class);
    private final ChecksumVerificationService service = new ChecksumVerificationService(repo, audit);

    private void stubInsert(int result) {
        when(repo.insertIfAbsent(any(), eq(tenant), eq(jobId), eq(ALGO), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(result);
    }

    private void stubExisting(String expected, String computed, boolean verified) {
        ChecksumRecord r = new ChecksumRecord(UUID.randomUUID(), tenant, jobId, ALGO, expected, computed, verified, Instant.now());
        when(repo.findByTransferJobIdAndAlgorithmAndTenantId(jobId, ALGO, tenant)).thenReturn(Optional.of(r));
    }

    @Test
    void firstRecordMatchingIsVerified() {
        stubInsert(1);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A)).isTrue();
        verify(audit).checksumResult(eq(tenant), anyString(), eq(jobId), eq(true), any());
    }

    @Test
    void firstRecordMismatchingIsNotVerified() {
        stubInsert(1);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, B)).isFalse();
        verify(audit).checksumResult(eq(tenant), anyString(), eq(jobId), eq(false), any());
    }

    @Test
    void comparisonIsCaseAndWhitespaceInsensitive() {
        stubInsert(1);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, " " + A.toUpperCase() + " ", A)).isTrue();
    }

    @Test
    void retryAfterResumeWithSameValuesCreatesNoDuplicateAndNoChange() {
        stubInsert(0);                     // a record already exists for (job, algorithm)
        stubExisting(A, A, true);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A)).isTrue();
        verify(repo, never()).save(any());
        verify(audit, never()).checksumResult(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void recordingTwiceAlwaysGoesThroughTheIdempotentInsert() {
        when(repo.insertIfAbsent(any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(1).thenReturn(0);
        stubExisting(A, A, true);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A)).isTrue();
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A)).isTrue();
        verify(repo, times(2)).insertIfAbsent(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void differentComputedValueAgainstVerifiedRecordIsAConflictNotAnOverwrite() {
        stubInsert(0);
        stubExisting(A, A, true);
        assertThatThrownBy(() -> service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, B))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKSUM_CONFLICT");
        verify(repo, never()).save(any());
    }

    @Test
    void differentExpectedValueIsAConflict() {
        stubInsert(0);
        stubExisting(A, A, true);
        assertThatThrownBy(() -> service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, B, B))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void previouslyUnverifiedRecordIsUpdatedInPlaceWhenRecomputedCorrectly() {
        stubInsert(0);
        stubExisting(A, B, false);
        assertThat(service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A)).isTrue();
        verify(repo).save(any(ChecksumRecord.class));   // updated, not duplicated
    }

    @Test
    void missingRecordAfterConflictingInsertIsReportedNotIgnored() {
        stubInsert(0);
        when(repo.findByTransferJobIdAndAlgorithmAndTenantId(jobId, ALGO, tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordAndVerify(tenant, "SYSTEM", jobId, ALGO, A, A))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void matchesRecordedRequiresAVerifiedRecordWithTheSameHash() {
        stubExisting(A, A, true);
        assertThat(service.matchesRecorded(tenant, jobId, ALGO, A)).isTrue();
        assertThat(service.matchesRecorded(tenant, jobId, ALGO, B)).isFalse();
    }

    @Test
    void matchesRecordedIsFalseWhenNoRecordOrUnverified() {
        when(repo.findByTransferJobIdAndAlgorithmAndTenantId(jobId, ALGO, tenant)).thenReturn(Optional.empty());
        assertThat(service.matchesRecorded(tenant, jobId, ALGO, A)).isFalse();
        stubExisting(A, A, false);
        assertThat(service.matchesRecorded(tenant, jobId, ALGO, A)).isFalse();
    }
}
