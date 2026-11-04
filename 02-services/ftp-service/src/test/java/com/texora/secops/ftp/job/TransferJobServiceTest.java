package com.texora.secops.ftp.job;

import com.texora.secops.ftp.admin.service.ServiceConfigService;
import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.EndpointDirection;
import com.texora.secops.ftp.domain.FtpEndpoint;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.domain.TransferProtocol;
import com.texora.secops.ftp.dto.SubmitTransferJobRequest;
import com.texora.secops.ftp.dto.SubmitTransferJobResponse;
import com.texora.secops.ftp.endpoint.service.EndpointAllowlistService;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.ftp.exception.TenantAccessDeniedException;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.exception.UpstreamUnavailableException;
import com.texora.secops.ftp.job.event.TransferJobSubmittedEvent;
import com.texora.secops.ftp.job.service.TransferJobService;
import com.texora.secops.ftp.repository.TransferAttemptRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.PolicyDecision;
import com.texora.secops.ftp.security.PolicyDecisionClient;
import com.texora.secops.iam.token.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransferJobServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID subject = UUID.randomUUID();
    private final UUID srcId = UUID.randomUUID();
    private final UUID dstId = UUID.randomUUID();

    private final TransferJobRepository jobs = mock(TransferJobRepository.class);
    private final TransferAttemptRepository attempts = mock(TransferAttemptRepository.class);
    private final EndpointAllowlistService endpoints = mock(EndpointAllowlistService.class);
    private final PolicyDecisionClient policy = mock(PolicyDecisionClient.class);
    private final ServiceConfigService config = mock(ServiceConfigService.class);
    private final FtpAuditTrail audit = mock(FtpAuditTrail.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TransferJobService service = new TransferJobService(jobs, attempts, endpoints, policy, config, audit,
            events, mock(PlatformTransactionManager.class));

    private CallerAuthentication caller;

    @BeforeEach
    void setUp() {
        TokenClaims claims = mock(TokenClaims.class);
        when(claims.getTenantId()).thenReturn(tenant);
        when(claims.getSubject()).thenReturn(subject);
        caller = CallerAuthentication.of(claims);
        when(config.inspectionMandatory(tenant)).thenReturn(true);
        when(config.inspectionDefaultRequired(tenant)).thenReturn(true);
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(srcId), eq(EndpointDirection.SOURCE)))
                .thenReturn(endpoint(srcId, EndpointDirection.SOURCE));
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(dstId), eq(EndpointDirection.DEST)))
                .thenReturn(endpoint(dstId, EndpointDirection.DEST));
        when(policy.decide(any(), any(), anyString(), anyString())).thenReturn(new PolicyDecision(true, false, "ALLOW"));
    }

    private FtpEndpoint endpoint(UUID id, EndpointDirection d) {
        return new FtpEndpoint(id, tenant, "sftp.example.com", 22, d, TransferProtocol.SFTP, "/", "ref", "SHA256:x", true,
                subject, Instant.now());
    }

    private SubmitTransferJobRequest request(Boolean inspection) {
        return new SubmitTransferJobRequest(srcId, dstId, "payroll.csv", inspection, null);
    }

    @Test
    void nonAllowlistedEndpointIsRejectedWith422BeforeAnythingElseHappens() {
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(srcId), eq(EndpointDirection.SOURCE)))
                .thenThrow(new UnprocessableException("ENDPOINT_NOT_ALLOWLISTED", "Endpoint is not on the allowlist"));

        assertThatThrownBy(() -> service.submit(caller, request(null)))
                .isInstanceOf(UnprocessableException.class)
                .extracting("code").isEqualTo("ENDPOINT_NOT_ALLOWLISTED");

        verify(jobs, never()).save(any());
        verifyNoInteractions(policy);
        verifyNoInteractions(events);
    }

    @Test
    void nonAllowlistedDestinationIsAlsoRejected() {
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(dstId), eq(EndpointDirection.DEST)))
                .thenThrow(new UnprocessableException("ENDPOINT_NOT_ALLOWLISTED", "Endpoint is not on the allowlist"));
        assertThatThrownBy(() -> service.submit(caller, request(null))).isInstanceOf(UnprocessableException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void anotherTenantsEndpointIsForbiddenNotEmpty() {
        when(endpoints.requireAllowlisted(eq(tenant), anyString(), eq(srcId), eq(EndpointDirection.SOURCE)))
                .thenThrow(new TenantAccessDeniedException("Endpoint belongs to another tenant"));
        assertThatThrownBy(() -> service.submit(caller, request(null))).isInstanceOf(TenantAccessDeniedException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void mandatoryInspectionCannotBeOptedOut() {
        assertThatThrownBy(() -> service.submit(caller, request(false)))
                .isInstanceOf(UnprocessableException.class).extracting("code").isEqualTo("INSPECTION_MANDATORY");
        verifyNoInteractions(policy);
        verify(jobs, never()).save(any());
    }

    @Test
    void policyDenyStopsTheTransfer() {
        when(policy.decide(any(), any(), anyString(), anyString())).thenReturn(new PolicyDecision(false, false, "POLICY_DENIED"));
        assertThatThrownBy(() -> service.submit(caller, request(null)))
                .isInstanceOf(RbacDeniedException.class).extracting("code").isEqualTo("POLICY_DENIED");
        verify(jobs, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void policyServiceOutageFailsClosed() {
        when(policy.decide(any(), any(), anyString(), anyString()))
                .thenReturn(new PolicyDecision(false, true, "POLICY_SERVICE_UNAVAILABLE"));
        assertThatThrownBy(() -> service.submit(caller, request(null))).isInstanceOf(UpstreamUnavailableException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void acceptedJobIsPersistedAsSubmittedWithCallersSubjectAndPublishesEvent() {
        SubmitTransferJobResponse response = service.submit(caller, request(null));

        ArgumentCaptor<TransferJob> saved = ArgumentCaptor.forClass(TransferJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(saved.getValue().getInitiatorSsoSubject()).isEqualTo(subject);
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenant);
        assertThat(saved.getValue().isRequireInspection()).isTrue();
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        verify(events).publishEvent(any(TransferJobSubmittedEvent.class));
    }

    @Test
    void cancelOfNonRunningJobIsAConflict() {
        UUID jobId = UUID.randomUUID();
        TransferJob done = new TransferJob(jobId, tenant, subject, srcId, dstId, "f.csv", true, null, null,
                JobStatus.COMPLETED, null, Instant.now(), Instant.now());
        when(jobs.findByIdAndTenantId(jobId, tenant)).thenReturn(Optional.of(done));
        when(jobs.transition(any(), any(), any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.cancel(caller, jobId)).isInstanceOf(ConflictException.class);
        verify(audit, never()).jobCancelled(any(), any(), any());
    }

    @Test
    void anotherTenantsJobIsForbidden() {
        UUID jobId = UUID.randomUUID();
        when(jobs.findByIdAndTenantId(jobId, tenant)).thenReturn(Optional.empty());
        when(jobs.existsById(jobId)).thenReturn(true);
        assertThatThrownBy(() -> service.get(tenant, jobId)).isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void unknownJobIsNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobs.findByIdAndTenantId(jobId, tenant)).thenReturn(Optional.empty());
        when(jobs.existsById(jobId)).thenReturn(false);
        assertThatThrownBy(() -> service.get(tenant, jobId))
                .isInstanceOf(com.texora.secops.ftp.exception.NotFoundException.class);
    }

    @Test
    void invalidStatusFilterIsRejected() {
        assertThatThrownBy(() -> service.list(tenant, "NOPE", 0, 10)).isInstanceOf(UnprocessableException.class);
    }
}
