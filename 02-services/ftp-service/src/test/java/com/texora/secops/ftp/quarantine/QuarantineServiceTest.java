package com.texora.secops.ftp.quarantine;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.InspectionOutcome;
import com.texora.secops.ftp.domain.InspectionResult;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.QuarantineRecord;
import com.texora.secops.ftp.domain.QuarantineStatus;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.dto.PlaceQuarantineRequest;
import com.texora.secops.ftp.dto.QuarantineRecordResponse;
import com.texora.secops.ftp.dto.ReleaseQuarantineRequest;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.ftp.exception.TenantAccessDeniedException;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.job.event.QuarantineDestroyedEvent;
import com.texora.secops.ftp.job.event.QuarantineReleasedEvent;
import com.texora.secops.ftp.quarantine.client.ContentInspectionClient;
import com.texora.secops.ftp.quarantine.client.InspectionUnavailableException;
import com.texora.secops.ftp.quarantine.client.InspectionVerdict;
import com.texora.secops.ftp.quarantine.service.QuarantineService;
import com.texora.secops.ftp.repository.InspectionResultRepository;
import com.texora.secops.ftp.repository.QuarantineRecordRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.iam.token.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantineServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID initiator = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    private final QuarantineRecordRepository quarantineRepo = mock(QuarantineRecordRepository.class);
    private final InspectionResultRepository inspectionRepo = mock(InspectionResultRepository.class);
    private final TransferJobRepository jobs = mock(TransferJobRepository.class);
    private final ContentInspectionClient client = mock(ContentInspectionClient.class);
    private final FtpAuditTrail audit = mock(FtpAuditTrail.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final QuarantineService service = new QuarantineService(quarantineRepo, inspectionRepo, jobs, client, audit,
            events, mock(PlatformTransactionManager.class));

    private TransferJob job;

    @BeforeEach
    void setUp() {
        job = new TransferJob(jobId, tenant, initiator, UUID.randomUUID(), UUID.randomUUID(), "payroll.csv", true, null, null,
                JobStatus.IN_PROGRESS, null, Instant.now(), Instant.now());
        when(jobs.transition(eq(jobId), eq(tenant), any(), any(), any(), any())).thenReturn(1);
        when(jobs.findByIdAndTenantId(jobId, tenant)).thenReturn(Optional.of(job));
    }

    private static CallerAuthentication caller(UUID tenant, UUID subject) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        return CallerAuthentication.of(c);
    }

    private void verdict(InspectionOutcome outcome) {
        when(client.inspect(any(), any(), any(), anyString())).thenReturn(new InspectionVerdict(outcome, "sandbox-1", "detail"));
    }

    private void assertQuarantinedWithReason(String reason) {
        ArgumentCaptor<QuarantineRecord> rec = ArgumentCaptor.forClass(QuarantineRecord.class);
        verify(quarantineRepo).save(rec.capture());
        assertThat(rec.getValue().getStatus()).isEqualTo(QuarantineStatus.QUARANTINED);
        assertThat(rec.getValue().getReason()).isEqualTo(reason);
        assertThat(rec.getValue().getReleasedBy()).isNull();
        verify(jobs).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.QUARANTINED), eq(reason), any());
    }

    // ------------------------------------------------------------------ Requirement 2: fail-closed inspection

    @Test
    void cleanVerdictIsTheOnlyWayToRelease() {
        verdict(InspectionOutcome.CLEAN);
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isTrue();
        verify(quarantineRepo, never()).save(any());
        verify(jobs, never()).transition(any(), any(), any(), eq(JobStatus.QUARANTINED), any(), any());
        ArgumentCaptor<InspectionResult> res = ArgumentCaptor.forClass(InspectionResult.class);
        verify(inspectionRepo).save(res.capture());
        assertThat(res.getValue().getOutcome()).isEqualTo(InspectionOutcome.CLEAN);
    }

    @Test
    void maliciousIsQuarantined() {
        verdict(InspectionOutcome.MALICIOUS);
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("MALICIOUS_CONTENT");
    }

    @Test
    void inconclusiveIsQuarantinedWithNoBenefitOfTheDoubt() {
        verdict(InspectionOutcome.INCONCLUSIVE);
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("INSPECTION_INCONCLUSIVE");
    }

    @Test
    void unavailableInspectionServiceQuarantinesInsteadOfSkipping() {
        when(client.inspect(any(), any(), any(), anyString())).thenThrow(new InspectionUnavailableException("down"));
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("INSPECTION_UNAVAILABLE");
    }

    @Test
    void anyUnexpectedExceptionAlsoQuarantines() {
        when(client.inspect(any(), any(), any(), anyString())).thenThrow(new IllegalStateException("anything"));
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("INSPECTION_UNAVAILABLE");
    }

    @Test
    void nullVerdictQuarantines() {
        when(client.inspect(any(), any(), any(), anyString())).thenReturn(null);
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("INSPECTION_UNAVAILABLE");
    }

    @Test
    void verdictWithNullOutcomeQuarantines() {
        when(client.inspect(any(), any(), any(), anyString())).thenReturn(new InspectionVerdict(null, "x", "y"));
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        assertQuarantinedWithReason("INSPECTION_UNAVAILABLE");
    }

    @Test
    void jobCancelledDuringInspectionCreatesNoRecordAndIsNotReleased() {
        when(jobs.transition(eq(jobId), eq(tenant), any(), any(), any(), any())).thenReturn(0);
        verdict(InspectionOutcome.MALICIOUS);
        assertThat(service.inspectStaged(job, Path.of("f"), "abc")).isFalse();
        verify(quarantineRepo, never()).save(any());
    }

    @Test
    void nothingInTheQuarantinePackageIsScheduled() {
        for (Class<?> c : new Class<?>[]{QuarantineService.class, ContentInspectionClient.class}) {
            for (Method m : c.getDeclaredMethods()) {
                assertThat(m.isAnnotationPresent(Scheduled.class))
                        .as(c.getSimpleName() + "." + m.getName() + " must not be @Scheduled (no auto-release)").isFalse();
            }
        }
    }

    // ------------------------------------------------------------------ Requirement 3: two-person release

    private QuarantineRecord record(QuarantineStatus status) {
        QuarantineRecord r = new QuarantineRecord(UUID.randomUUID(), tenant, jobId, "MALICIOUS_CONTENT", Instant.now(), null,
                null, status, null);
        when(quarantineRepo.findByIdAndTenantId(r.getId(), tenant)).thenReturn(Optional.of(r));
        return r;
    }

    private static ReleaseQuarantineRequest action(String a) {
        return new ReleaseQuarantineRequest(a, "reviewed by security");
    }

    @Test
    void initiatorCannotReleaseOwnQuarantinedFile() {
        QuarantineRecord r = record(QuarantineStatus.QUARANTINED);
        assertThatThrownBy(() -> service.release(caller(tenant, initiator), r.getId(), action("RELEASE")))
                .isInstanceOf(RbacDeniedException.class).extracting("code").isEqualTo("SELF_RELEASE_BLOCKED");

        verify(audit).quarantineReleaseDenied(eq(tenant), eq(initiator.toString()), eq(r.getId()), anyString());
        verify(quarantineRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
        assertThat(r.getStatus()).isEqualTo(QuarantineStatus.QUARANTINED);
    }

    @Test
    void initiatorCannotDestroyOwnQuarantinedFileEither() {
        QuarantineRecord r = record(QuarantineStatus.QUARANTINED);
        assertThatThrownBy(() -> service.release(caller(tenant, initiator), r.getId(), action("DESTROY")))
                .isInstanceOf(RbacDeniedException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void differentReviewerCanReleaseAndDeliveryIsTriggered() {
        QuarantineRecord r = record(QuarantineStatus.QUARANTINED);
        QuarantineRecordResponse response = service.release(caller(tenant, reviewerId), r.getId(), action("RELEASE"));

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        assertThat(r.getReleasedBy()).isEqualTo(reviewerId);
        assertThat(r.getReleasedAt()).isNotNull();
        verify(audit).quarantineReleased(eq(tenant), eq(reviewerId.toString()), eq(r.getId()), any());
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue()).isInstanceOf(QuarantineReleasedEvent.class);
    }

    @Test
    void differentReviewerCanDestroyWhichFailsTheJobAndPurgesStaging() {
        QuarantineRecord r = record(QuarantineStatus.QUARANTINED);
        QuarantineRecordResponse response = service.release(caller(tenant, reviewerId), r.getId(), action("DESTROY"));

        assertThat(response.getStatus()).isEqualTo("DESTROYED");
        verify(jobs).transition(eq(jobId), eq(tenant), any(), eq(JobStatus.FAILED), eq("QUARANTINE_DESTROYED"), any());
        verify(audit).quarantineDestroyed(eq(tenant), eq(reviewerId.toString()), eq(r.getId()), any());
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue()).isInstanceOf(QuarantineDestroyedEvent.class);
    }

    @Test
    void alreadyResolvedRecordIsAConflict() {
        QuarantineRecord r = record(QuarantineStatus.RELEASED);
        assertThatThrownBy(() -> service.release(caller(tenant, reviewerId), r.getId(), action("RELEASE")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anotherTenantsRecordIsForbidden() {
        UUID id = UUID.randomUUID();
        when(quarantineRepo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        when(quarantineRepo.existsById(id)).thenReturn(true);
        assertThatThrownBy(() -> service.release(caller(tenant, reviewerId), id, action("RELEASE")))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void unknownActionIsRejected() {
        QuarantineRecord r = record(QuarantineStatus.QUARANTINED);
        assertThatThrownBy(() -> service.release(caller(tenant, reviewerId), r.getId(), action("ARCHIVE")))
                .isInstanceOf(UnprocessableException.class);
        verify(quarantineRepo, never()).save(any());
    }

    @Test
    void manualPlacementQuarantinesARunningJobAndIsAudited() {
        QuarantineRecordResponse r = service.placeManually(caller(tenant, reviewerId),
                new PlaceQuarantineRequest(jobId, "suspicious sender"));
        assertThat(r.getStatus()).isEqualTo("QUARANTINED");
        verify(audit).quarantinePlaced(eq(tenant), eq(reviewerId.toString()), any(), any());
    }

    @Test
    void manualPlacementOfAFinishedJobIsAConflict() {
        when(jobs.transition(eq(jobId), eq(tenant), any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.placeManually(caller(tenant, reviewerId), new PlaceQuarantineRequest(jobId, "x")))
                .isInstanceOf(ConflictException.class);
        verify(quarantineRepo, never()).save(any());
    }

    @Test
    void invalidStatusFilterIsRejected() {
        assertThatThrownBy(() -> service.list(tenant, "NOPE", 0, 10)).isInstanceOf(UnprocessableException.class);
    }
}
